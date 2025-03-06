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

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Message;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.MockRepositoryExportException;
import io.github.microcks.util.MockRepositoryExporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock repository exporter that exports Microcks domain objects definitions into the APIExamples YAML format.
 * @author laurent
 */
public class ExamplesExporter implements MockRepositoryExporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ExamplesExporter.class);

   private final ObjectMapper mapper;
   private Service service;
   private final Map<Operation, List<? extends Exchange>> operationsExchanges = new HashMap<>();

   /** Create a new ExamplesExporter. */
   public ExamplesExporter() {
      mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
   }

   @Override
   public void addServiceDefinition(Service service) throws MockRepositoryExportException {
      if (this.service != null) {
         log.error("ExamplesExporter only allows one service definition");
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
      rootNode.put("apiVersion", "mocks.microcks.io/v1alpha1");
      rootNode.put("kind", "APIExamples");
      rootNode.set("metadata",
            mapper.createObjectNode().put("name", service.getName()).put("version", service.getVersion()));


      ObjectNode operationsNode = rootNode.putObject("operations");
      for (Operation operation : operationsExchanges.keySet()) {
         // Initialize a node for the operation.
         ObjectNode operationNode = operationsNode.putObject(operation.getName());
         List<? extends Exchange> exchanges = operationsExchanges.get(operation);

         for (Exchange exchange : exchanges) {
            exportExchange(operationNode, exchange);
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

   private void exportExchange(ObjectNode operationNode, Exchange exchange) {
      if (ServiceType.EVENT.equals(service.getType())) {
         UnidirectionalEvent event = (UnidirectionalEvent) exchange;
         exportUnidirectionalEvent(operationNode, event);
      } else {
         RequestResponsePair pair = (RequestResponsePair) exchange;
         exportRequestResponsePair(operationNode, pair);
      }
   }

   private void exportUnidirectionalEvent(ObjectNode operationNode, UnidirectionalEvent event) {
      EventMessage eventMessage = event.getEventMessage();
      ObjectNode exchangeNode = operationNode.putObject(eventMessage.getName());
      ObjectNode messageNode = exchangeNode.putObject("eventMessage");

      exportHeaders(messageNode, eventMessage);
      messageNode.put("payload", eventMessage.getContent());
   }

   private void exportRequestResponsePair(ObjectNode operationNode, RequestResponsePair pair) {
      Request request = pair.getRequest();
      Response response = pair.getResponse();

      ObjectNode exchangeNode = operationNode.putObject(request.getName());
      ObjectNode requestNode = exchangeNode.putObject("request");
      ObjectNode responseNode = exchangeNode.putObject("response");

      // Export the request into the request node.
      exportHeaders(requestNode, request);
      if (request.getQueryParameters() != null && !request.getQueryParameters().isEmpty()) {
         ObjectNode parametersNode = requestNode.putObject("parameters");
         for (Parameter parameter : request.getQueryParameters()) {
            parametersNode.put(parameter.getName(), parameter.getValue());
         }
      }
      if (request.getContent() != null) {
         requestNode.put("body", request.getContent());
      }

      // Export the response into the response node.
      exportHeaders(responseNode, response);
      if (response.getStatus() != null) {
         responseNode.put("status", response.getStatus());
      }
      if (response.getMediaType() != null) {
         responseNode.put("mediaType", response.getMediaType());
      }
      if (response.getContent() != null) {
         responseNode.put("body", response.getContent());
      }
   }

   private void exportHeaders(ObjectNode messageNode, Message message) {
      if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
         ObjectNode headersNode = messageNode.putObject("headers");
         for (Header header : message.getHeaders()) {
            headersNode.put(header.getName(), header.getValues().stream().findFirst().get());
         }
      }
   }
}
