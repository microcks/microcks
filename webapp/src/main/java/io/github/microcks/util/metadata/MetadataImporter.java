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
package io.github.microcks.util.metadata;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Mock repository importer that uses a {@code APIMetadata} YAML descriptor as a source artifact.
 * @author laurent
 */
public class MetadataImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MetadataImporter.class);

   private final JsonNode spec;

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local APIMetadata spec file
    * @throws IOException if project file cannot be found or read.
    */
   public MetadataImporter(String specificationFilePath) throws IOException {
      try {
         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(specificationFilePath));
         String specContent = new String(bytes, StandardCharsets.UTF_8);

         // Convert them to Node using Jackson object mapper.
         ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
         spec = mapper.readTree(specContent.getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
         log.error("Exception while parsing APIMetadata specification file " + specificationFilePath, e);
         throw new IOException("APIMetadata spec file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      JsonNode metadataNode = spec.get("metadata");
      if (metadataNode == null) {
         log.error("Missing mandatory metadata in {}", spec.asText());
         throw new MockRepositoryImportException("Mandatory metadata property is missing in APIMetadata");
      }
      service.setName(metadataNode.path("name").asText());
      service.setVersion(metadataNode.path("version").asText());

      Metadata metadata = new Metadata();
      MetadataExtractor.completeMetadata(metadata, metadataNode);
      service.setMetadata(metadata);

      // Then build its operations.
      service.setOperations(extractOperations());

      result.add(service);
      return result;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      return new ArrayList<>();
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      return new ArrayList<>();
   }

   /**
    * Extract the list of operations from Specification.
    */
   private List<Operation> extractOperations() {
      List<Operation> results = new ArrayList<>();

      // Iterate on specification "operations" nodes.
      Iterator<Map.Entry<String, JsonNode>> operations = spec.path("operations").fields();
      while (operations.hasNext()) {
         Map.Entry<String, JsonNode> operation = operations.next();

         // Build a new operation.
         Operation op = new Operation();
         op.setName(operation.getKey());

         JsonNode operationValue = operation.getValue();
         MetadataExtractor.completeOperationProperties(op, operationValue);

         results.add(op);
      }
      return results;
   }
}
