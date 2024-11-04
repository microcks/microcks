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

package io.github.microcks.util.asyncapi;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.AbstractJsonRepositoryImporter;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.ReferenceResolver;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.metadata.MetadataExtensions;
import io.github.microcks.util.metadata.MetadataExtractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static io.github.microcks.util.asyncapi.AsyncAPICommons.*;

/**
 * An implementation of MockRepositoryImporter that deals with AsyncAPI v3.0.x specification file ; whether encoding
 * into JSON or YAML documents.
 * @author laurent
 */
public class AsyncAPI3Importer extends AbstractJsonRepositoryImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AsyncAPI3Importer.class);

   private static final List<String> VALID_VERBS = Arrays.asList("send", "receive");


   /**
    * Build a new importer.
    * @param specificationFilePath The path to local AsyncAPI spec file
    * @param referenceResolver     An optional resolver for references present into the AsyncAPI file
    * @throws IOException if project file cannot be found or read.
    */
   public AsyncAPI3Importer(String specificationFilePath, ReferenceResolver referenceResolver) throws IOException {
      super(specificationFilePath, referenceResolver);
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      service.setName(rootSpecification.path("info").path("title").asText());
      service.setVersion(rootSpecification.path("info").path("version").asText());
      service.setType(ServiceType.EVENT);

      // Complete metadata if specified via extension.
      if (rootSpecification.path("info").has(MetadataExtensions.MICROCKS_EXTENSION)) {
         Metadata metadata = new Metadata();
         MetadataExtractor.completeMetadata(metadata,
               rootSpecification.path("info").path(MetadataExtensions.MICROCKS_EXTENSION));
         service.setMetadata(metadata);
      }

      // Before extraction operations, we need to get and build external reference if we have a resolver.
      initializeReferencedResources(service);

      // Then build its operations.
      service.setOperations(extractOperations());

      result.add(service);
      return result;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();

      // Build a suitable name.
      String name = service.getName() + "-" + service.getVersion();
      if (Boolean.TRUE.equals(isYaml)) {
         name += ".yaml";
      } else {
         name += ".json";
      }

      // Build a brand-new resource just with spec content.
      Resource resource = new Resource();
      resource.setName(name);
      resource.setType(ResourceType.ASYNC_API_SPEC);
      results.add(resource);
      // Set the content of main OpenAPI that may have been updated with normalized dependencies with initializeReferencedResources().
      resource.setContent(rootSpecificationContent);

      // Add the external resources that were imported during service discovery.
      results.addAll(externalResources);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      List<Exchange> messageDefs = new ArrayList<>();

      // Retrieve default content type, defaulting to application/json.
      String defaultContentType = "application/json";
      if (rootSpecification.has("defaultContentType")) {
         defaultContentType = rootSpecification.get("defaultContentType").asText("application/json");
      }

      // Iterate on specification "operations" nodes.
      Iterator<Map.Entry<String, JsonNode>> operations = rootSpecification.path("operations").fields();
      while (operations.hasNext()) {
         Map.Entry<String, JsonNode> operationEntry = operations.next();
         JsonNode operationNode = operationEntry.getValue();

         // Got to filter out for current operation only.
         String action = operationNode.path("action").asText();
         String operationName = action.toUpperCase() + " " + operationEntry.getKey();

         if (operationName.equals(operation.getName()) && operationNode.path(MESSAGES).isArray()) {
            // Search for event messages.
            List<EventMessage> eventMessages = buildEventMessages(operationNode, defaultContentType);

            if (eventMessages != null && !eventMessages.isEmpty()) {
               // Update dispatch information if necessary.
               completeDispatchingCriteria(operation, operationNode, eventMessages);

               eventMessages.stream().forEach(eventMessage -> messageDefs.add(new UnidirectionalEvent(eventMessage)));
            }
            break;
         }
      }
      return messageDefs;
   }

   /** Extract the list of operations from Specification. */
   private List<Operation> extractOperations() {
      List<Operation> results = new ArrayList<>();

      // Iterate on specification "operations" nodes.
      Iterator<Map.Entry<String, JsonNode>> operations = rootSpecification.path("operations").fields();
      while (operations.hasNext()) {
         Map.Entry<String, JsonNode> operationEntry = operations.next();
         String operationShortName = operationEntry.getKey();

         JsonNode operationNode = operationEntry.getValue();
         String action = operationNode.path("action").asText();
         if (VALID_VERBS.contains(action)) {
            // Build and add this operation to the list.
            Operation operation = buildValidOperation(operationShortName, action, operationNode);
            results.add(operation);
         }
      }

      return results;
   }

   /** Build a single operation having its name, action and Json. */
   private Operation buildValidOperation(String name, String action, JsonNode operationNode) {
      String operationName = action.toUpperCase() + " " + name;

      Operation operation = new Operation();
      operation.setName(operationName);
      operation.setMethod(action.toUpperCase());

      // Complete operation properties if any.
      if (operationNode.has(MetadataExtensions.MICROCKS_OPERATION_EXTENSION)) {
         MetadataExtractor.completeOperationProperties(operation,
               operationNode.path(MetadataExtensions.MICROCKS_OPERATION_EXTENSION));
      }

      // Look for bindings at the operation level.
      if (operationNode.has(BINDINGS)) {
         AsyncAPICommons.completeOperationLevelBindings(operation, operationNode.get(BINDINGS));
      }

      // Then, check the related channels and extract dispatching info and bindings.
      if (operationNode.path(CHANNEL_NODE).isObject()) {
         JsonNode channel = operationNode.get(CHANNEL_NODE);
         JsonNode channelNode = followRefIfAny(channel);

         if (channelNode.has(ADDRESS_NODE)) {
            String address = channelNode.get(ADDRESS_NODE).asText();
            if (AsyncAPICommons.channelAddressHasParts(address)) {
               operation.setDispatcher(DispatchStyles.URI_PARTS);
               operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromStringPattern(address));
            }
            operation.addResourcePath(address);
         }

         if (channelNode.has(BINDINGS)) {
            AsyncAPICommons.completeChannelLevelBindings(operation, channelNode.get(BINDINGS));
         }
      }

      // Then, check the related messages and complete bindings.
      if (operationNode.path(MESSAGES).isArray()) {
         Iterator<JsonNode> messages = operationNode.path(MESSAGES).elements();
         while (messages.hasNext()) {
            JsonNode messageInChannelNode = followRefIfAny(messages.next());
            JsonNode messageNode = followRefIfAny(messageInChannelNode);
            if (messageNode.has(BINDINGS)) {
               AsyncAPICommons.completeMessageLevelBindings(operation, messageNode.get(BINDINGS));
            }
         }
      }

      return operation;
   }

   /** If necessary, complete an operation and its messages dispatch information. */
   private void completeDispatchingCriteria(Operation operation, JsonNode operationNode,
         List<EventMessage> eventMessages) {
      // Update dispatch information if necessary.
      if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {

         // Retrieve information on channel address and parameters.
         JsonNode channelNode = followRefIfAny(operationNode.get(CHANNEL_NODE));
         String address = channelNode.get(ADDRESS_NODE).asText();

         // We have 2 cases here: parameter value can be dynamic, expressed with a location in message
         // or parameter value can be static, expressed as an examples.
         List<AsyncAPIParameter> dynamicParameters = getDynamicParameters(channelNode);
         Map<String, Map<String, String>> parametersByMessage = getParametersByMessage(channelNode);
         ObjectMapper mapper = getObjectMapper(true);

         for (EventMessage eventMessage : eventMessages) {
            // Start initializing message parameter values with the static ones.
            Map<String, String> parameterValues = parametersByMessage.getOrDefault(eventMessage.getName(),
                  new HashMap<>());

            // Extract each dynamic parameter with its value coming from message.
            for (AsyncAPIParameter parameter : dynamicParameters) {
               String parameterValue = null;

               try {
                  if (parameter.location().startsWith("$message.payload#")) {
                     String location = parameter.location().substring("$message.payload#".length());
                     JsonNode eventMessageRootNode = mapper.readTree(eventMessage.getContent());
                     parameterValue = eventMessageRootNode.at(location).asText();
                  }
               } catch (Exception e) {
                  log.warn("Failed to extract the location value in {} from message {} for operation {}",
                        parameter.location(), eventMessage.getName(), operation.getName());
                  log.warn("Pursuing with the other ones but dispatch will be incomplete");
               }
               if (parameterValue != null) {
                  parameterValues.put(parameter.name(), parameterValue);
               }
            }

            // UPdate operation resource paths and message dispatch criteria.
            String resourcePath = URIBuilder.buildURIFromPattern(address, parameterValues);
            operation.addResourcePath(resourcePath);
            eventMessage.setDispatchCriteria(DispatchCriteriaHelper.buildFromPartsMap(address, parameterValues));
         }
      }
   }

   /** Given a Channel Json node, get its dynamic parameter definitions (those having a location). */
   private List<AsyncAPIParameter> getDynamicParameters(JsonNode channelNode) {
      List<AsyncAPIParameter> results = new ArrayList<>();

      if (channelNode.path(PARAMETERS_NODE).isObject()) {
         Iterator<Entry<String, JsonNode>> parameters = channelNode.get(PARAMETERS_NODE).fields();
         while (parameters.hasNext()) {
            Entry<String, JsonNode> parameterEntry = parameters.next();
            JsonNode parameter = followRefIfAny(parameterEntry.getValue());

            String parameterName = parameterEntry.getKey();

            if (parameter.has(LOCATION_NODE)) {
               if (log.isDebugEnabled()) {
                  log.debug("Processing param {} for channel {}, with location {}", parameterName,
                        channelNode.get("address").asText(), parameter.get(LOCATION_NODE).asText());
               }
               results.add(new AsyncAPIParameter(parameterName, parameter.get(LOCATION_NODE).asText()));
            }
         }
      }
      return results;
   }

   /**
    * Given a Channel Json node, get its static parameter values (those without a location), organized by message
    * example name. Key of value map is parameter name. Value of value map is parameter value ;-)
    */
   private Map<String, Map<String, String>> getParametersByMessage(JsonNode channelNode) {
      Map<String, Map<String, String>> results = new HashMap<>();

      if (channelNode.path(PARAMETERS_NODE).isObject()) {
         Iterator<Entry<String, JsonNode>> parameters = channelNode.get(PARAMETERS_NODE).fields();
         while (parameters.hasNext()) {
            Entry<String, JsonNode> parameterEntry = parameters.next();
            JsonNode parameter = followRefIfAny(parameterEntry.getValue());
            String parameterName = parameterEntry.getKey();
            if (!parameter.has(LOCATION_NODE) && parameter.path(EXAMPLES_NODE).isArray()) {
               Iterator<JsonNode> examples = parameter.get(EXAMPLES_NODE).elements();
               while (examples.hasNext()) {
                  String example = examples.next().asText();
                  if (example.contains(":")) {
                     String exampleKey = example.substring(0, example.indexOf(":"));
                     String exampleValue = example.substring(example.indexOf(":") + 1);

                     if (log.isDebugEnabled()) {
                        log.debug("Processing param {} for channel {} for message {}", parameterName,
                              channelNode.get("address").asText(), exampleKey);
                     }

                     Map<String, String> exampleParams = results.getOrDefault(exampleKey, new HashMap<>());
                     exampleParams.put(parameterName, exampleValue);
                     results.put(exampleKey, exampleParams);
                  }
               }
            }
         }
      }
      return results;
   }


   /** Build a list of EventMessages from an operation Json node. */
   private List<EventMessage> buildEventMessages(JsonNode operationNode, String defaultContentType) {
      List<EventMessage> eventMessages = null;
      Iterator<JsonNode> messages = operationNode.path(MESSAGES).elements();
      while (messages.hasNext()) {
         JsonNode operationMessageNode = messages.next();
         JsonNode messageInChannelNode = followRefIfAny(operationMessageNode);
         JsonNode messageNode = followRefIfAny(messageInChannelNode);

         // Get message content type.
         String contentType = defaultContentType;
         if (messageNode.has("contentType")) {
            contentType = messageNode.path("contentType").asText();
         }

         // Retrieve the messageName from message ref found in operation.
         String messageName = operationMessageNode.path("$ref").textValue();

         if (messageName != null && messageNode.has(EXAMPLES_NODE)) {
            // Compute a short message name if examples have no name attribute.
            messageName = messageName.substring(messageName.lastIndexOf("/") + 1);
            eventMessages = buildEventMessageFromExamples(messageName, contentType, messageNode.get(EXAMPLES_NODE));
         }
      }
      return eventMessages;
   }

   /** Build a list of EventMessages from a Message examples Json node. */
   private List<EventMessage> buildEventMessageFromExamples(String messageName, String contentType,
         JsonNode examplesNode) {
      List<EventMessage> exchanges = new ArrayList<>();

      Iterator<JsonNode> examples = examplesNode.elements();
      while (examples.hasNext()) {
         JsonNode exampleNode = examples.next();

         EventMessage eventMessage = new EventMessage();
         // Use name attribute if present, otherwise generate from message name.
         if (exampleNode.has("name")) {
            eventMessage.setName(exampleNode.get("name").asText());
         } else {
            eventMessage.setName(messageName + "-" + (exchanges.size() + 1));
         }

         eventMessage.setMediaType(contentType);
         eventMessage.setContent(getExamplePayload(exampleNode));

         // Now complete with specified headers.
         List<Header> headers = AsyncAPICommons.getExampleHeaders(exampleNode);
         for (Header header : headers) {
            eventMessage.addHeader(header);
         }

         exchanges.add(eventMessage);
      }
      return exchanges;
   }

   /**
    * Get the value of an example. This can be direct value field or those of followed $ref.
    */
   private String getExamplePayload(JsonNode example) {
      if (example.has(EXAMPLE_PAYLOAD_NODE)) {
         return getValueString(example.path(EXAMPLE_PAYLOAD_NODE));
      }
      if (example.has("$payloadRef")) {
         // $ref: '#/components/examples/param_laurent'
         String ref = example.path("$payloadRef").asText();
         JsonNode component = rootSpecification.at(ref.substring(1));
         return getExamplePayload(component);
      }
      return null;
   }

   private record AsyncAPIParameter(String name, String location) {
   }
}
