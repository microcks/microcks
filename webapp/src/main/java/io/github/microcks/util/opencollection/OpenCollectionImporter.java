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
      collectOperations(collection.path("items"), operations);
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

   private void collectOperations(JsonNode items, Map<String, Operation> operations) {
      for (JsonNode item : items) {
         String type = item.path("info").path("type").asText();
         if ("folder".equals(type)) {
            collectOperations(item.path("items"), operations);
         } else if ("http".equals(type)) {
            JsonNode http = item.path("http");
            String method = http.path("method").asText().strip().toUpperCase(Locale.ROOT);
            String name = method + " " + OpenCollectionPathExtractor.extractPath(http.path("url").asText());
            Operation operation = new Operation();
            operation.setName(name);
            operation.setMethod(method);
            operations.putIfAbsent(name, operation);
         }
      }
   }
}
