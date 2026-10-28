/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.util.opencollection;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Message;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.MockRepositoryImporterFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for class OpenCollectionImporter.
 * @author SebastienDegodez
 */
class OpenCollectionImporterTest {

   @Test
   void testSimpleProjectImport() {
      File collection = new File("../samples/PetstoreAPI-opencollection.yml");
      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(collection, null));

      // Check that basic service properties are there.
      List<Service> services = assertDoesNotThrow(importer::getServiceDefinitions);
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("Petstore API", service.getName());
      assertEquals("1.0", service.getVersion());
      assertEquals(ServiceType.REST, service.getType());

      // Check that operations have been found in folders, in document order and with the path kept verbatim.
      assertEquals(List.of("GET /pet/findByStatus", "GET /pet/:petId", "POST /pet"),
            service.getOperations().stream().map(Operation::getName).toList());
      Operation findByStatus = service.getOperations().get(0);
      assertEquals("GET", findByStatus.getMethod());
      Operation getById = service.getOperations().get(1);
      Operation addPet = service.getOperations().get(2);
      assertEquals("POST", addPet.getMethod());

      // Check that the collection is exposed, untouched, as the only resource.
      List<Resource> resources = assertDoesNotThrow(() -> importer.getResourceDefinitions(service));
      assertEquals(1, resources.size());
      assertEquals("Petstore API-1.0.yaml", resources.get(0).getName());
      assertEquals(ResourceType.OPEN_COLLECTION, resources.get(0).getType());
      byte[] uploaded = assertDoesNotThrow(() -> Files.readAllBytes(collection.toPath()));
      assertArrayEquals(uploaded, resources.get(0).getContent().getBytes(UTF_8));

      // Check that each operation gets only its own examples, in document order.
      Response available = responsesOf(importer, service, findByStatus).get(0);
      assertEquals("available response", available.getName());
      assertTrue(available.getContent().contains("\"id\": 190192062"));

      List<Response> getByIdResponses = responsesOf(importer, service, getById);
      assertEquals(List.of("get-2", "get-1"), getByIdResponses.stream().map(Response::getName).toList());
      Response found = getByIdResponses.get(0);
      assertEquals("200", found.getStatus());
      assertEquals("application/json", found.getMediaType());
      assertEquals(Map.of("Content-Type", Set.of("application/json"), "X-Rate-Limit", Set.of("10")), headersOf(found));
      Response notFound = getByIdResponses.get(1);
      assertEquals("404", notFound.getStatus());
      assertEquals("", notFound.getContent());

      List<Response> addPetResponses = responsesOf(importer, service, addPet);
      assertEquals(List.of("pet created"), addPetResponses.stream().map(Response::getName).toList());
      assertEquals("201", addPetResponses.get(0).getStatus());

      // Check the request side: example headers, params and raw body, kept as written.
      Request getTwo = requestsOf(importer, service, getById).get(0);
      assertEquals("get-2", getTwo.getName());
      assertEquals(Map.of("Accept", Set.of("application/json")), headersOf(getTwo));
      assertEquals(List.of("petId=2"),
            getTwo.getQueryParameters().stream().map(param -> param.getName() + "=" + param.getValue()).toList());
      assertEquals("{\"name\":\"cat\",\"photoUrls\":[\"string\"],\"status\":\"available\"}",
            requestsOf(importer, service, addPet).get(0).getContent());

      // Check that operations with examples get their path, verbatim, as resource path.
      assertEquals(Set.of("/pet/findByStatus"), findByStatus.getResourcePaths());
      assertEquals(Set.of("/pet/:petId"), getById.getResourcePaths());
      assertEquals(Set.of("/pet"), addPet.getResourcePaths());
   }

   @Test
   void testBundledFalseIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-bundled-false.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("not yet supported"));
   }

   @Test
   void testUnsupportedSpecVersionIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-spec-2.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("Only OpenCollection 1.x"));
      assertTrue(exception.getMessage().contains("2.0.0"));
   }

   @Test
   void testNumericOpenCollectionPropertyIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-numeric-opencollection.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("'opencollection' property must be a string"));
   }

   @Test
   void testNotAnOpenCollectionIsRejected() {
      OpenCollectionImporter importer = importerFor("not-an-opencollection.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage()
            .contains("Not an OpenCollection document: 'opencollection' root property is missing"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "petstore-no-version.yml", "petstore-blank-version.yml" })
   void testMissingOrBlankVersionIsRejected(String fixture) {
      OpenCollectionImporter importer = importerFor(fixture);

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains(OpenCollectionImporter.VERSION_MISSING_MESSAGE));
   }

   @Test
   void testNumericVersionIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-numeric-version.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("must be a string"));
   }

   @Test
   void testMissingNameIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-no-name.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("Name property"));
   }

   @Test
   void testItemsWithoutTypeAreResolvedStructurally() {
      assertEquals(List.of("GET /pet/findByStatus", "GET /pet/:petId"), operationNamesOf("items-without-type.yml"));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "{info: {name: Skipped, type: graphql}} | graphql",
         "{info: {name: Skipped, type: grpc}} | grpc", "{info: {name: Skipped, type: websocket}} | websocket",
         "{type: script, path: ./setup.js} | script", "{info: {name: Skipped, type: app}} | app",
         "{info: {name: Skipped, type: teleport}} | teleport" })
   void testUnsupportedItemKindsAreSkipped(String unsupportedItem, String expectedType, @TempDir Path directory)
         throws IOException {
      Path collection = directory.resolve("unsupported-item.yml");
      Files.writeString(collection, """
            opencollection: 1.0.0
            info:
              name: Petstore API
              version: "1.0"
            items:
              - %s
              - info: {name: Find pets by status, type: http}
                http: {method: GET, url: "{{baseUrl}}/pet/findByStatus"}
            """.formatted(unsupportedItem), UTF_8);
      OpenCollectionImporter importer = assertDoesNotThrow(() -> new OpenCollectionImporter(collection.toString()));

      List<String> warnings = new ArrayList<>();
      List<Operation> operations = warningsCapturedWhile(() -> assertDoesNotThrow(importer::getServiceDefinitions),
            warnings).get(0).getOperations();

      assertEquals(1, operations.size());
      assertEquals("GET /pet/findByStatus", operations.get(0).getName());
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("is not supported yet, skipping"));
      assertTrue(warnings.get(0).contains("of type '" + expectedType + "'"));
   }

   @Test
   void testNestedFoldersAreTraversed() {
      assertEquals(List.of("GET /pet/:petId"), operationNamesOf("nested-folders.yml"));
   }

   @Test
   void testCollectionWithoutRequestsHasNoOperation() {
      assertEquals(List.of(), operationNamesOf("no-requests.yml"));
   }

   @Test
   void testUnidentifiableItemIsSkipped() {
      OpenCollectionImporter importer = importerFor("unidentifiable-item.yml");
      List<String> warnings = new ArrayList<>();

      List<Service> services = warningsCapturedWhile(() -> assertDoesNotThrow(importer::getServiceDefinitions),
            warnings);

      assertEquals(0, services.get(0).getOperations().size());
      assertEquals(List.of("Item 'Mystery' of type 'unknown' is not supported yet, skipping"), warnings);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
         "items-not-a-list-root.yml | 'items' property must be a list | <root>",
         "items-not-a-list-folder.yml | 'items' property must be a list | pet",
         "item-not-an-object.yml | item must be an object | #0" })
   void testMalformedItemsAreRejected(String fixture, String reason, String location) {
      OpenCollectionImporter importer = importerFor(fixture);

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains(reason));
      assertTrue(exception.getMessage().endsWith(" at '" + location + "'"));
   }

   @Test
   void testHttpItemWithoutUrlIsRejected() {
      OpenCollectionImporter importer = importerFor("http-item-without-url.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("http.url"));
      assertTrue(exception.getMessage().contains("pet/Get pet by id"));
   }

   /** A declared http type wins over the structure: an http item without http section is rejected, not skipped. */
   @ParameterizedTest
   @ValueSource(strings = { "http-item-without-method.yml", "http-item-without-http-section.yml" })
   void testHttpItemWithoutMethodIsRejected(String fixture) {
      OpenCollectionImporter importer = importerFor(fixture);

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("http.method"));
      assertTrue(exception.getMessage().contains("pet/Get pet by id"));
   }

   @Test
   void testMethodWithWhitespaceIsRejected() {
      OpenCollectionImporter importer = importerFor("method-with-whitespace.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("must not contain whitespace"));
      assertTrue(exception.getMessage().contains("'GET PET'"));
      assertTrue(exception.getMessage().contains("pet/Get pet by id"));
   }

   @Test
   void testDuplicateOperationsCollapse() {
      assertEquals(List.of("GET /pet/findByStatus"), operationNamesOf("duplicate-operations.yml"));
   }

   @Test
   void testLowerCaseMethodIsUppercased() {
      OpenCollectionImporter importer = importerFor("lowercase-method.yml");

      Service service = assertDoesNotThrow(importer::getServiceDefinitions).get(0);

      assertEquals("GET /pet/findByStatus", service.getOperations().get(0).getName());
      assertEquals("GET", service.getOperations().get(0).getMethod());
   }

   /** The path of the url is kept verbatim (ADR-001): neither variables, case, slashes nor encoding are normalised. */
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = { "{{baseUrl}}/pet/{{petId}} | /pet/{{petId}}", "{{baseUrl}}/pet/ | /pet/",
         "{{baseUrl}}/Pet//Find | /Pet//Find", "{{baseUrl}}/pet/%7Bid%7D | /pet/%7Bid%7D",
         "'  {{baseUrl}}/pet  ' | /pet", "{{host}}:{{port}}/pet | /pet", "http://localhost:8080 | /",
         "https:///pet | /pet", "?status=available | /" })
   void testOperationNameFromUrl(String address, String path, @TempDir Path directory) throws IOException {
      OpenCollectionImporter importer = importerForGetRequests(directory, address);

      Service service = assertDoesNotThrow(importer::getServiceDefinitions).get(0);

      assertEquals(List.of("GET " + path), service.getOperations().stream().map(Operation::getName).toList());
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = { "{{baseUrl}}pet | no path found after host prefix",
         "{{baseUrl}}/pet shop | path contains whitespace" })
   void testUnderivablePathIsRejected(String address, String reason, @TempDir Path directory) throws IOException {
      OpenCollectionImporter importer = importerForGetRequests(directory, address);

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage()
            .contains("Cannot derive an operation path from OpenCollection 'http.url' '" + address + "'"));
      assertTrue(exception.getMessage().contains(reason));
   }

   @Test
   void testJsonRenderingIsImported() {
      File collection = new File(
            "target/test-classes/io/github/microcks/util/opencollection/petstore-opencollection.json");
      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(collection, null));

      Service service = assertDoesNotThrow(importer::getServiceDefinitions).get(0);

      assertEquals("Petstore API", service.getName());
      assertEquals(List.of("GET /pet/findByStatus", "GET /pet/:petId"),
            service.getOperations().stream().map(Operation::getName).toList());
      // The resource name keeps the yaml extension, whatever the rendering of the source.
      Resource resource = assertDoesNotThrow(() -> importer.getResourceDefinitions(service)).get(0);
      assertEquals("Petstore API-1.0.yaml", resource.getName());
   }

   @Test
   void testIncompleteExamplesAreCompletedOrSkipped() {
      OpenCollectionImporter importer = importerFor("examples-incomplete.yml");
      Service service = assertDoesNotThrow(importer::getServiceDefinitions).get(0);

      List<String> warnings = new ArrayList<>();
      List<Response> responses = warningsCapturedWhile(
            () -> responsesOf(importer, service, service.getOperations().get(0)), warnings);

      assertEquals(List.of("Find pet by ID 1", "get-1", "Find pet by ID 3", "no status"),
            responses.stream().map(Response::getName).toList());
      assertEquals(List.of("404", "400", "200"), responses.stream().skip(1).map(Response::getStatus).toList());
      assertEquals(2, warnings.size());
      assertTrue(warnings.get(0).contains("'get-1'"));
      assertTrue(warnings.get(0).contains("already used"));
      assertTrue(warnings.get(1).contains("'no answer'"));
      assertTrue(warnings.get(1).contains("has no response"));
   }

   @ParameterizedTest
   @CsvSource({ "content-type header,application/vnd.petstore+json", "json,application/json", "xml,application/xml",
         "text,text/plain", "html,text/html", "binary,application/octet-stream" })
   void testMediaTypeComesFromContentTypeHeaderThenBodyType(String example, String mediaType) {
      OpenCollectionImporter importer = importerFor("examples-media-type-from-body-type.yml");
      Service service = assertDoesNotThrow(importer::getServiceDefinitions).get(0);

      Response response = responsesOf(importer, service, service.getOperations().get(0)).stream()
            .filter(candidate -> example.equals(candidate.getName())).findFirst().orElseThrow();

      assertEquals(mediaType, response.getMediaType());
   }

   private static OpenCollectionImporter importerFor(String fixture) {
      return assertDoesNotThrow(
            () -> new OpenCollectionImporter("target/test-classes/io/github/microcks/util/opencollection/" + fixture));
   }

   private static List<Response> responsesOf(MockRepositoryImporter importer, Service service, Operation operation) {
      List<Exchange> exchanges = assertDoesNotThrow(() -> importer.getMessageDefinitions(service, operation));
      return exchanges.stream().map(exchange -> ((RequestResponsePair) exchange).getResponse()).toList();
   }

   private static List<Request> requestsOf(MockRepositoryImporter importer, Service service, Operation operation) {
      List<Exchange> exchanges = assertDoesNotThrow(() -> importer.getMessageDefinitions(service, operation));
      return exchanges.stream().map(exchange -> ((RequestResponsePair) exchange).getRequest()).toList();
   }

   private static Map<String, Set<String>> headersOf(Message message) {
      if (message.getHeaders() == null) {
         return Map.of();
      }
      return message.getHeaders().stream().collect(Collectors.toMap(Header::getName, Header::getValues));
   }

   private static List<String> operationNamesOf(String fixture) {
      Service service = assertDoesNotThrow(importerFor(fixture)::getServiceDefinitions).get(0);
      return service.getOperations().stream().map(Operation::getName).toList();
   }

   private static <T> T warningsCapturedWhile(Supplier<T> action, List<String> warnings) {
      Logger importerLogger = (Logger) LoggerFactory.getLogger(OpenCollectionImporter.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      importerLogger.addAppender(appender);
      try {
         return action.get();
      } finally {
         importerLogger.detachAppender(appender);
         appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
               .forEach(event -> warnings.add(event.getFormattedMessage()));
      }
   }

   private static OpenCollectionImporter importerForGetRequests(Path directory, String... addresses)
         throws IOException {
      StringBuilder collection = new StringBuilder("""
            opencollection: 1.0.0
            info:
              name: Petstore API
              version: "1.0"
            items:
            """);
      for (int index = 0; index < addresses.length; index++) {
         collection.append("  - info: {name: Request %d, type: http}\n".formatted(index));
         collection.append("    http: {method: GET, url: \"%s\"}\n".formatted(addresses[index]));
      }
      Path file = directory.resolve("collection.yml");
      Files.writeString(file, collection, UTF_8);
      return assertDoesNotThrow(() -> new OpenCollectionImporter(file.toString()));
   }
}
