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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.util.MockRepositoryExportException;
import io.github.microcks.util.MockRepositoryExporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock repository exporter that exports Microcks domain objects definitions into the Open API Overlay YAML format.
 * @author laurent
 */
public class OpenAPIOverlayExporter implements MockRepositoryExporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenAPIOverlayExporter.class);

   private final ObjectMapper mapper;
   private Service service;
   private final Map<Operation, List<? extends Exchange>> operationsExchanges = new HashMap<>();

   /** Create a new OpenAPIOverlayExporter. */
   public OpenAPIOverlayExporter() {
      mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
   }

   @Override
   public void addServiceDefinition(Service service) throws MockRepositoryExportException {
      if (this.service != null) {
         log.error("OpenAPIOverlayExporter only allows one service definition");
         throw new MockRepositoryExportException("Service definition already set");
      }
      this.service = service;
   }

   @Override
   public void addMessageDefinitions(Service service, Operation operation, List<? extends Exchange> exchanges)
         throws MockRepositoryExportException {
      if (this.service != null && !this.service.getId().equals(service.getId())) {
         log.error("ExamplesExporter only allows one service definition, this one is different");
         throw new MockRepositoryExportException("Service definition doesn't match with the one already set");
      }
      if (this.service == null) {
         this.service = service;
      }
      this.operationsExchanges.put(operation, exchanges);
   }

   @Override
   public String exportAsString() throws MockRepositoryExportException {
      ObjectNode rootNode = mapper.createObjectNode();

      // Initialize header and metadata.
      rootNode.put("overlay", "1.0.0");
      rootNode.set("info", mapper.createObjectNode().put("title", service.getName() + " Overlay for examples")
            .put("version", service.getVersion()));

      ArrayNode actionsNode = rootNode.putArray("actions");
      for (Operation operation : operationsExchanges.keySet()) {
         // Each exchange is translated into an update action.
         List<? extends Exchange> exchanges = operationsExchanges.get(operation);

         for (Exchange exchange : exchanges) {
            if (exchange instanceof RequestResponsePair pair) {
               exportRequest(actionsNode, operation, pair.getRequest());
               exportResponse(actionsNode, operation, pair.getResponse());
            }
         }
      }

      // Output the root node.
      String yamlResult = null;
      try {
         yamlResult = mapper.writeValueAsString(rootNode);
      } catch (Exception e) {
         log.error("Exception while writing YAML export", e);
         throw new MockRepositoryExportException("Exception while writing YAML export", e);
      }
      return yamlResult;
   }

   private void exportRequest(ArrayNode actions, Operation operation, Request request) {
      String[] operationParts = operation.getName().split(" ");
      String pathPart = operationParts[1];
      String methodPart = operationParts[0].toLowerCase();

      // Export the parameters part of the exchange if present.
      if (request.getQueryParameters() != null && !request.getQueryParameters().isEmpty()) {
         for (Parameter parameter : request.getQueryParameters()) {
            // Export the query parameter part of the exchange.
            ObjectNode parameterNode = actions.addObject();
            parameterNode.put("target",
                  "$.paths['" + pathPart + "']." + methodPart + ".parameters[?@.name=='" + parameter.getName() + "']");
            createExampleUpdate(parameterNode, request.getName(), parameter.getValue());
         }
      }

      // Export the requestBody part of the exchange if present.
      if (request.getContent() != null) {
         ObjectNode requestNode = actions.addObject();
         requestNode.put("target",
               "$.paths['" + pathPart + "']." + methodPart + ".requestBody.content['" + getContentType(request) + "']");
         createExampleUpdate(requestNode, request.getName(), request.getContent());
      }
   }

   private String getContentType(Request request) {
      if (request.getHeaders() != null) {
         for (Header header : request.getHeaders()) {
            if ("Content-Type".equalsIgnoreCase(header.getName())) {
               return header.getValues().iterator().next();
            }
         }
      }
      return "application/json";
   }

   private void exportResponse(ArrayNode actions, Operation operation, Response response) {
      String[] operationParts = operation.getName().split(" ");
      String pathPart = operationParts[1];
      String methodPart = operationParts[0].toLowerCase();

      // Export the response part of the exchange.
      ObjectNode responseNode = actions.addObject();
      responseNode.put("target", "$.paths['" + pathPart + "']." + methodPart + ".responses['" + response.getStatus()
            + "'].content['" + response.getMediaType() + "']");
      createExampleUpdate(responseNode, response.getName(), response.getContent());
   }

   /**
    * Build the {@code update} block for an overlay action targeting a parameter, requestBody or response. The shape
    * matches what standard OpenAPI overlay mergers (e.g. github.com/speakeasy-api/openapi/overlay/loader) deep-merge
    * cleanly into the target node, regardless of whether an {@code examples} sub-node already exists there.
    * @param actionNode The overlay action root, into which an {@code update} object will be added.
    * @param name       The example's human-readable name, used both as the slugified key and as the {@code summary}.
    * @param value      The example's value payload.
    */
   private void createExampleUpdate(ObjectNode actionNode, String name, String value) {
      ObjectNode updateNode = actionNode.putObject("update");
      ObjectNode examplesNode = updateNode.putObject("examples");
      ObjectNode exampleNode = examplesNode.putObject(toExampleKey(name));
      exampleNode.put("value", value);
      exampleNode.put("summary", name);
   }

   private String toExampleKey(String name) {
      return name.toLowerCase().replace(" ", "-");
   }
}
