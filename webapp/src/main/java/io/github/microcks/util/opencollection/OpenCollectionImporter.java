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
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An implementation of MockRepositoryImporter that deals with OpenCollection specification files.
 * @author SebastienDegodez
 */
public class OpenCollectionImporter implements MockRepositoryImporter {

   /** Name of the root property marking an OpenCollection document. */
   public static final String OPENCOLLECTION_PROPERTY = "opencollection";

   /** Prefix of the supported OpenCollection specification versions (major version 1). */
   public static final String SUPPORTED_MAJOR_VERSION_PREFIX = "1.";

   /** Message of the error raised when the document has no opencollection root property. */
   public static final String NOT_AN_OPENCOLLECTION_MESSAGE = "Not an OpenCollection document: 'opencollection' "
         + "root property is missing";

   /** Message of the error raised when the opencollection property is not a string. */
   public static final String OPENCOLLECTION_NOT_STRING_MESSAGE = "OpenCollection 'opencollection' property must be "
         + "a string (quote it, e.g. opencollection: \"1.0.0\")";

   /** Message of the error raised when the OpenCollection specification major version is not supported. */
   public static final String UNSUPPORTED_SPEC_VERSION_MESSAGE = "Only OpenCollection 1.x documents are supported";

   /** Message of the error raised when the service name is missing or blank. */
   public static final String NAME_MISSING_MESSAGE = "Name property is missing in OpenCollection info";

   /** Message of the error raised when the service version is missing or blank. */
   public static final String VERSION_MISSING_MESSAGE = "Version property is missing in OpenCollection info";

   /** Message of the error raised when the service version is not a string. */
   public static final String VERSION_NOT_STRING_MESSAGE = "Version property in OpenCollection info must be a string "
         + "(quote it, e.g. version: \"1.0\")";

   /** Message of the error raised when the collection declares itself as a multi-file one. */
   public static final String BUNDLED_FALSE_NOT_SUPPORTED_MESSAGE = "OpenCollection with 'bundled: false' "
         + "(multi-file collection) is not yet supported. Please import a bundled single-file OpenCollection";

   /** Message of the error raised when the bundled property is not a boolean. */
   public static final String BUNDLED_NOT_BOOLEAN_MESSAGE = "OpenCollection 'bundled' property must be a boolean";

   /** Message of the error raised when an http item has no method. */
   public static final String HTTP_METHOD_MISSING_MESSAGE = "OpenCollection http item has no 'http.method'";

   /** Message of the error raised when an http method contains whitespace. */
   public static final String HTTP_METHOD_INVALID_MESSAGE = "OpenCollection 'http.method' must not contain whitespace";

   /** Message of the error raised when no operation path can be derived from an http url. */
   public static final String HTTP_URL_INVALID_MESSAGE = "Cannot derive an operation path from OpenCollection 'http.url'";

   /** Message of the error raised when an http item has no url. */
   public static final String HTTP_URL_MISSING_MESSAGE = "OpenCollection http item has no 'http.url'";

   /** The type reported in logs for an item that does not declare one. */
   private static final String UNKNOWN_ITEM_TYPE = "unknown";

   /** Message of the error raised when an items property is not a list. */
   public static final String ITEMS_NOT_A_LIST_MESSAGE = "OpenCollection 'items' property must be a list";

   /** Message of the error raised when an item is not an object. */
   public static final String ITEM_NOT_AN_OBJECT_MESSAGE = "OpenCollection item must be an object";

   /** The location reported in errors for the root items of the collection. */
   private static final String ROOT_LOCATION = "<root>";

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenCollectionImporter.class);

   private JsonNode collection;

   /** The collection text exactly as uploaded: it is stored as the contract and never re-serialised. */
   private String collectionContent;

   /**
    * Build a new importer.
    * @param collectionFilePath The path to OpenCollection file
    * @throws IOException if project file cannot be found or read.
    */
   public OpenCollectionImporter(String collectionFilePath) throws IOException {
      try {
         byte[] yamlBytes = Files.readAllBytes(Paths.get(collectionFilePath));
         collectionContent = new String(yamlBytes, StandardCharsets.UTF_8);
         collection = ObjectMapperFactory.getYamlObjectMapper().readTree(yamlBytes);
      } catch (Exception e) {
         log.error("Exception while parsing OpenCollection file {}", collectionFilePath, e);
         throw new IOException("OpenCollection file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      checkSpecVersion();
      checkBundled();
      checkServiceIdentity();
      Service service = new Service();
      JsonNode info = collection.path("info");
      service.setName(info.path("name").asText());
      service.setVersion(info.path("version").asText());
      service.setType(ServiceType.REST);

      Map<String, Operation> operations = new LinkedHashMap<>();
      collectOperations(collection.path("items"), "", operations);
      service.setOperations(new ArrayList<>(operations.values()));
      return List.of(service);
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      Resource resource = new Resource();
      resource.setName(service.getName() + "-" + service.getVersion() + ".yaml");
      resource.setType(ResourceType.OPEN_COLLECTION);
      resource.setContent(collectionContent);
      return List.of(resource);
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      return List.of();
   }

   private void checkSpecVersion() throws MockRepositoryImportException {
      JsonNode specNode = collection.path(OPENCOLLECTION_PROPERTY);
      if (specNode.isMissingNode()) {
         throw new MockRepositoryImportException(NOT_AN_OPENCOLLECTION_MESSAGE);
      }
      if (!specNode.isTextual()) {
         throw new MockRepositoryImportException(OPENCOLLECTION_NOT_STRING_MESSAGE);
      }
      String specVersion = specNode.textValue();
      if (!specVersion.startsWith(SUPPORTED_MAJOR_VERSION_PREFIX)) {
         throw new MockRepositoryImportException(UNSUPPORTED_SPEC_VERSION_MESSAGE + " (found '" + specVersion + "')");
      }
   }

   private void checkBundled() throws MockRepositoryImportException {
      JsonNode bundled = collection.path("bundled");
      if (bundled.isMissingNode()) {
         return;
      }
      if (!bundled.isBoolean()) {
         throw new MockRepositoryImportException(BUNDLED_NOT_BOOLEAN_MESSAGE);
      }
      if (!bundled.booleanValue()) {
         throw new MockRepositoryImportException(BUNDLED_FALSE_NOT_SUPPORTED_MESSAGE);
      }
   }

   private void checkServiceIdentity() throws MockRepositoryImportException {
      JsonNode info = collection.path("info");
      JsonNode name = info.path("name");
      if (!name.isTextual() || name.textValue().isBlank()) {
         throw new MockRepositoryImportException(NAME_MISSING_MESSAGE);
      }
      JsonNode version = info.path("version");
      if (version.isMissingNode()) {
         throw new MockRepositoryImportException(VERSION_MISSING_MESSAGE);
      }
      if (!version.isTextual()) {
         throw new MockRepositoryImportException(VERSION_NOT_STRING_MESSAGE);
      }
      if (version.textValue().isBlank()) {
         throw new MockRepositoryImportException(VERSION_MISSING_MESSAGE);
      }
   }

   private void collectOperations(JsonNode items, String parentLocation, Map<String, Operation> operations)
         throws MockRepositoryImportException {
      if (items.isMissingNode()) {
         return;
      }
      if (!items.isArray()) {
         throw new MockRepositoryImportException(
               ITEMS_NOT_A_LIST_MESSAGE + locationSuffix(parentLocation.isEmpty() ? ROOT_LOCATION : parentLocation));
      }
      for (int index = 0; index < items.size(); index++) {
         collectItemOperations(items.get(index), itemLocation(parentLocation, items.get(index), index), operations);
      }
   }

   private void collectItemOperations(JsonNode item, String location, Map<String, Operation> operations)
         throws MockRepositoryImportException {
      if (!item.isObject()) {
         throw new MockRepositoryImportException(ITEM_NOT_AN_OBJECT_MESSAGE + locationSuffix(location));
      }
      switch (kindOf(item)) {
         case FOLDER -> collectOperations(item.path("items"), location, operations);
         case HTTP -> addHttpOperation(item.path("http"), location, operations);
         case UNSUPPORTED ->
            log.warn("Item '{}' of type '{}' is not supported yet, skipping", location, declaredTypeOf(item));
      }
   }

   private static boolean isNonBlankText(JsonNode node) {
      return node.isTextual() && !node.textValue().isBlank();
   }

   private static String locationSuffix(String location) {
      return " at '" + location + "'";
   }

   private static String itemLocation(String parentLocation, JsonNode item, int index) {
      JsonNode name = item.path("info").path("name");
      String label = name.isTextual() ? name.textValue() : "#" + index;
      return parentLocation.isEmpty() ? label : parentLocation + "/" + label;
   }

   /** The type an item declares, in <code>info.type</code> or at its root (ScriptFile), else "unknown". */
   private static String declaredTypeOf(JsonNode item) {
      JsonNode infoType = item.path("info").path("type");
      if (infoType.isTextual()) {
         return infoType.textValue();
      }
      JsonNode rootType = item.path("type");
      return rootType.isTextual() ? rootType.textValue() : UNKNOWN_ITEM_TYPE;
   }

   private static ItemKind kindOf(JsonNode item) {
      JsonNode type = item.path("info").path("type");
      if (type.isTextual()) {
         return ItemKind.fromType(type.textValue());
      }
      if (item.has("http")) {
         return ItemKind.HTTP;
      }
      return item.has("items") ? ItemKind.FOLDER : ItemKind.UNSUPPORTED;
   }

   private void addHttpOperation(JsonNode http, String location, Map<String, Operation> operations)
         throws MockRepositoryImportException {
      String method = methodOf(http, location);
      String name = method + " " + pathOf(urlOf(http, location), location);
      Operation operation = new Operation();
      operation.setName(name);
      operation.setMethod(method);
      operations.putIfAbsent(name, operation);
   }

   private static String pathOf(String url, String location) throws MockRepositoryImportException {
      try {
         return OpenCollectionPathExtractor.extractPath(url);
      } catch (IllegalArgumentException e) {
         throw new MockRepositoryImportException(
               HTTP_URL_INVALID_MESSAGE + " '" + url + "'" + locationSuffix(location) + ": " + e.getMessage(), e);
      }
   }

   private static String methodOf(JsonNode http, String location) throws MockRepositoryImportException {
      if (!isNonBlankText(http.path("method"))) {
         throw new MockRepositoryImportException(HTTP_METHOD_MISSING_MESSAGE + locationSuffix(location));
      }
      String method = http.path("method").textValue().strip();
      if (method.chars().anyMatch(Character::isWhitespace)) {
         throw new MockRepositoryImportException(
               HTTP_METHOD_INVALID_MESSAGE + " '" + method + "'" + locationSuffix(location));
      }
      return method.toUpperCase(Locale.ROOT);
   }

   private static String urlOf(JsonNode http, String location) throws MockRepositoryImportException {
      if (!isNonBlankText(http.path("url"))) {
         throw new MockRepositoryImportException(HTTP_URL_MISSING_MESSAGE + locationSuffix(location));
      }
      return http.path("url").textValue();
   }

   /** The kinds of items the importer distinguishes. */
   private enum ItemKind {
      HTTP,
      FOLDER,
      UNSUPPORTED;

      static ItemKind fromType(String type) {
         return switch (type) {
            case "http" -> HTTP;
            case "folder" -> FOLDER;
            default -> UNSUPPORTED;
         };
      }
   }
}
