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
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.ReferenceResolver;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.metadata.MetadataExtensions;
import io.github.microcks.util.metadata.MetadataExtractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static io.github.microcks.util.asyncapi.AsyncAPICommons.*;

/**
 * An implementation of MockRepositoryImporter that deals with AsyncAPI v2.0.x specification file ; whether encoding
 * into JSON or YAML documents.
 * @author laurent
 */
public class AsyncAPIImporter extends AbstractJsonRepositoryImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AsyncAPIImporter.class);

   private static final String[] MULTI_STRUCTURES = { "allOf", "anyOf", "oneOf" };
   private static final List<String> VALID_VERBS = Arrays.asList("subscribe", "publish");

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local AsyncAPI spec file
    * @param referenceResolver     An optional resolver for references present into the AsyncAPI file
    * @throws IOException if project file cannot be found or read.
    */
   public AsyncAPIImporter(String specificationFilePath, ReferenceResolver referenceResolver) throws IOException {
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

      // Then build its operations.
      service.setOperations(extractOperations());

      result.add(service);
      return result;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();
      Map<String, Resource> resolvedExternalRefResources = new HashMap<>();

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

      // Browser operations messages and message traits to see if we have
      // references for external schemas only if we have a resolver available.
      if (referenceResolver != null) {
         for (Operation operation : service.getOperations()) {
            String[] operationElements = operation.getName().split(" ");
            String messageNamePtr = "/channels/" + operationElements[1].replace("/", "~1");
            messageNamePtr += "/" + operationElements[0].toLowerCase() + "/message";

            JsonNode messageNode = rootSpecification.at(messageNamePtr);
            if (messageNode != null) {
               // If it's a $ref, then navigate to it.
               messageNode = followRefIfAny(messageNode);

               // Extract payload schema here.
               if (messageNode.has(EXAMPLE_PAYLOAD_NODE)) {
                  JsonNode payloadNode = messageNode.path(EXAMPLE_PAYLOAD_NODE);

                  // Check we have a reference that is not a local one.
                  if (payloadNode.has("$ref")) {

                     Set<String> references = new HashSet<>();
                     findAllExternalRefsAsyncAPI(payloadNode, references);

                     for (String ref : references) {
                        // We may have already resolved it if referenced more than once.
                        Resource schemaResource = resolvedExternalRefResources.get(ref);
                        if (schemaResource == null) {
                           try {
                              // Remove trailing anchor marker (we may have this in Avro schema to point exact
                              // Resource)
                              if (ref.contains("#")) {
                                 ref = ref.substring(0, ref.indexOf("#"));
                              }

                              // Extract content using resolver.
                              String content = referenceResolver.getReferenceContent(ref, StandardCharsets.UTF_8);
                              String resourceName = ref.substring(ref.lastIndexOf('/') + 1);

                              // Build a new resource from content. Use the escaped operation path.
                              schemaResource = new Resource();
                              schemaResource.setName(IdBuilder.buildResourceFullName(service, resourceName));
                              schemaResource.setPath(ref);
                              schemaResource.setContent(content);

                              // We have to look at schema format to know the type.
                              if (messageNode.has("schemaFormat")) {
                                 String schemaFormat = messageNode.path("schemaFormat").asText();

                                 if (schemaFormat.startsWith("application/vnd.aai.asyncapi")) {
                                    schemaResource.setType(ResourceType.ASYNC_API_SCHEMA);
                                 } else if (schemaFormat.startsWith("application/vnd.oai.openapi")) {
                                    schemaResource.setType(ResourceType.OPEN_API_SCHEMA);
                                 } else if (schemaFormat.startsWith("application/schema+json")
                                       || schemaFormat.startsWith("application/schema+yaml")) {
                                    schemaResource.setType(ResourceType.JSON_SCHEMA);
                                 } else if (schemaFormat.startsWith("application/vnd.apache.avro")) {
                                    schemaResource.setType(ResourceType.AVRO_SCHEMA);
                                 }
                              } else {
                                 // We should probably go deeper here and inspect the content of resolved
                                 // resource
                                 // to actually get the real schema type...
                                 schemaResource.setType(ResourceType.JSON_SCHEMA);
                              }

                              if (!ref.startsWith("http")) {
                                 // If a relative resource, replace with new name.
                                 rootSpecificationContent = rootSpecificationContent.replace(ref,
                                       URLEncoder.encode(schemaResource.getName(), "UTF-8"));
                              }

                              results.add(schemaResource);
                           } catch (IOException ioe) {
                              log.error("IOException while trying to resolve reference " + ref, ioe);
                              log.info("Ignoring the reference {} cause it could not be resolved", ref);
                           }
                           // Mark it as resolved.
                           resolvedExternalRefResources.put(ref, schemaResource);
                        }

                        if (schemaResource != null) {
                           schemaResource.addOperation(operation.getName());
                        } else {
                           log.warn("Cannot add operation because schema resourse is null");
                        }
                     }
                  }
               }
            }
         }
         // Finally try to clean up resolved references and associated resources (files)
         referenceResolver.cleanResolvedReferences();
      }
      // Set the content of main AsyncAPI that may have been updated with dereferenced
      // dependencies.
      resource.setContent(rootSpecificationContent);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      List<Exchange> result = new ArrayList<>();

      // Retrieve default content type, defaulting to application/json.
      String defaultContentType = "application/json";
      if (rootSpecification.has("defaultContentType")) {
         defaultContentType = rootSpecification.get("defaultContentType").asText("application/json");
      }

      // Iterate on specification "channels" nodes.
      Iterator<Entry<String, JsonNode>> channels = rootSpecification.path("channels").fields();
      while (channels.hasNext()) {
         Entry<String, JsonNode> channel = channels.next();
         String channelName = channel.getKey();
         Map<String, Map<String, String>> pathParametersByExample = extractParametersByExample(channel.getValue());

         // Iterate on specification path, "verbs" nodes.
         Iterator<Entry<String, JsonNode>> verbs = channel.getValue().fields();
         while (verbs.hasNext()) {
            Entry<String, JsonNode> verb = verbs.next();
            String verbName = verb.getKey();

            // Find the correct operation.
            if (operation.getName().equals(verbName.toUpperCase() + " " + channelName.trim())) {
               JsonNode messageBody = verb.getValue().path("message");

               // If it's a $ref or multi-structure (oneOf, anyOf, allOf), then navigate to
               // them.
               List<JsonNode> messageBodies = followRefsIfAny(messageBody);

               for (JsonNode extractedMsgBody : messageBodies) {
                  // Get message content type.
                  String contentType = defaultContentType;
                  if (extractedMsgBody.has("contentType")) {
                     contentType = extractedMsgBody.path("contentType").asText();
                  }
                  // No need to go further if no examples.
                  if (extractedMsgBody.has(EXAMPLES_NODE)) {
                     Iterator<JsonNode> examples = extractedMsgBody.path(EXAMPLES_NODE).elements();
                     int exampleIndex = 0;
                     while (examples.hasNext()) {
                        JsonNode exampleNode = examples.next();

                        EventMessage eventMessage = null;
                        if (exampleNode.has("name")) {
                           // As of AsyncAPI 2.1.0 () we can now have a 'name' property for examples!
                           eventMessage = extractFromAsyncAPI21Example(contentType, exampleNode);
                        } else if (exampleNode.has(EXAMPLE_PAYLOAD_NODE)) {
                           // As of https://github.com/microcks/microcks/issues/385, we should support the restriction
                           // coming from AsyncAPI GItHub master revision and associated tooling...
                           eventMessage = extractFromAsyncAPIExample(contentType, exampleNode,
                                 channelName.trim() + "-" + exampleIndex);
                        } else {
                           eventMessage = extractFromMicrocksExample(contentType, exampleNode);
                        }
                        // If import succeed, deal with the dispatching criteria stuffs and
                        // add this event message as a valid event in results exchanges.
                        if (eventMessage != null) {
                           if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {
                              String resourcePathPattern = channelName;
                              Map<String, String> parts = pathParametersByExample.get(eventMessage.getName());
                              String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parts);
                              operation.addResourcePath(resourcePath);
                              eventMessage.setDispatchCriteria(
                                    DispatchCriteriaHelper.buildFromPartsMap(operation.getDispatcherRules(), parts));
                           }

                           result.add(new UnidirectionalEvent(eventMessage));
                        }
                        exampleIndex++;
                     }
                  }
               }
            }
         }
      }
      return result;
   }

   /** Extract the list of operations from Specification. */
   private List<Operation> extractOperations() {
      List<Operation> results = new ArrayList<>();

      // Iterate on specification "channels" nodes.
      Iterator<Entry<String, JsonNode>> channels = rootSpecification.path("channels").fields();
      while (channels.hasNext()) {
         Entry<String, JsonNode> channel = channels.next();
         String channelName = channel.getKey();

         // Iterate on specification path, "verbs" nodes.
         Iterator<Entry<String, JsonNode>> verbs = channel.getValue().fields();
         while (verbs.hasNext()) {
            Entry<String, JsonNode> verb = verbs.next();
            String verbName = verb.getKey();

            // Only deal with real verbs for now.
            if (VALID_VERBS.contains(verbName)) {
               String operationName = verbName.toUpperCase() + " " + channelName.trim();

               Operation operation = new Operation();
               operation.setName(operationName);
               operation.setMethod(verbName.toUpperCase());

               // Complete operation properties if any.
               if (verb.getValue().has(MetadataExtensions.MICROCKS_OPERATION_EXTENSION)) {
                  MetadataExtractor.completeOperationProperties(operation,
                        verb.getValue().path(MetadataExtensions.MICROCKS_OPERATION_EXTENSION));
               }

               // Deal with dispatcher stuffs.
               if (operation.getDispatcher() == null && channelHasParts(channelName)) {
                  operation.setDispatcher(DispatchStyles.URI_PARTS);
                  operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(channelName));
               } else {
                  operation.addResourcePath(channelName);
               }

               // We have to look also for bindings. First at the upper channel level.
               if (channel.getValue().has(BINDINGS)) {
                  AsyncAPICommons.completeChannelLevelBindings(operation, channel.getValue().get(BINDINGS));
               }

               // Then look for bindings at the operation level.
               if (verb.getValue().has(BINDINGS)) {
                  AsyncAPICommons.completeOperationLevelBindings(operation, verb.getValue().get(BINDINGS));
               }

               // Then look for bindings at the message level.
               JsonNode messageBody = verb.getValue().path("message");
               messageBody = followRefIfAny(messageBody);
               if (messageBody.has(BINDINGS)) {
                  AsyncAPICommons.completeMessageLevelBindings(operation, messageBody.get(BINDINGS));
               }

               results.add(operation);
            }
         }
      }

      return results;
   }

   /** Browse Json node to extract references and store them into externalRefs. */
   private void findAllExternalRefsAsyncAPI(JsonNode node, Set<String> externalRefs) {
      // If node as a $ref child, it's a stop condition.
      if (node.has("$ref")) {
         String ref = node.path("$ref").asText();
         if (!ref.startsWith("#")) {
            externalRefs.add(ref);
         } else {
            findAllExternalRefsAsyncAPI(followRefIfAny(node), externalRefs);
         }
      } else {
         // Iterate on all other children.
         Iterator<JsonNode> children = node.elements();
         while (children.hasNext()) {
            findAllExternalRefsAsyncAPI(children.next(), externalRefs);
         }
      }
   }

   /** Extract example using the AsyncAPI 2.1 new 'name' property. */
   private EventMessage extractFromAsyncAPI21Example(String contentType, JsonNode exampleNode) {
      // Retrieve name & payload value.
      String exampleName = exampleNode.path("name").asText();
      String exampleValue = getExamplePayload(exampleNode);

      // Build and store a request object.
      EventMessage eventMessage = new EventMessage();
      eventMessage.setName(exampleName);
      eventMessage.setContent(exampleValue);
      eventMessage.setMediaType(contentType);

      // Now complete with specified headers.
      List<Header> headers = AsyncAPICommons.getExampleHeaders(exampleNode);
      for (Header header : headers) {
         eventMessage.addHeader(header);
      }

      return eventMessage;
   }

   /** Extract example using the AsyncAPI master branch restrictions. */
   private EventMessage extractFromAsyncAPIExample(String contentType, JsonNode exampleNode, String exampleName) {
      // Retrieve payload value.
      String exampleValue = getExamplePayload(exampleNode);

      // Build and store a request object.
      EventMessage eventMessage = new EventMessage();
      eventMessage.setName(exampleName);
      eventMessage.setContent(exampleValue);
      eventMessage.setMediaType(contentType);

      // Now complete with specified headers.
      List<Header> headers = AsyncAPICommons.getExampleHeaders(exampleNode);
      for (Header header : headers) {
         eventMessage.addHeader(header);
      }

      return eventMessage;
   }

   /** Extract example using the Microcks (and Apicurio) extended notation. */
   private EventMessage extractFromMicrocksExample(String contentType, JsonNode exampleNode) {
      EventMessage eventMessage = null;

      Iterator<String> exampleNames = exampleNode.fieldNames();
      while (exampleNames.hasNext()) {
         String exampleName = exampleNames.next();
         JsonNode example = exampleNode.path(exampleName);

         // No need to go further if no payload.
         if (example.has(EXAMPLE_PAYLOAD_NODE)) {
            String exampleValue = getExamplePayload(example);

            // Build and store a request object.
            eventMessage = new EventMessage();
            eventMessage.setName(exampleName);
            eventMessage.setContent(exampleValue);
            eventMessage.setMediaType(contentType);

            // Now complete with specified headers.
            List<Header> headers = AsyncAPICommons.getExampleHeaders(example);
            for (Header header : headers) {
               eventMessage.addHeader(header);
            }
         }
      }
      return eventMessage;
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

   /** Check variables parts presence into given channel. */
   private static boolean channelHasParts(String channel) {
      return (channel.indexOf("/{") != -1);
   }

   /**
    * Extract parameters within a channel node and organize them by example. Key of value map is param name. Value of
    * value map is param value ;-)
    */
   private Map<String, Map<String, String>> extractParametersByExample(JsonNode node) {
      Map<String, Map<String, String>> results = new HashMap<>();
      Iterator<Entry<String, JsonNode>> parameters = node.path("parameters").fields();
      while (parameters.hasNext()) {
         Entry<String, JsonNode> parameterEntry = parameters.next();
         JsonNode parameter = parameterEntry.getValue();

         // If parameter is a $ref, navigate to it first.
         parameter = followRefIfAny(parameter);

         String parameterName = parameterEntry.getKey();
         log.debug("Processing param {}", parameterName);

         if (parameter.has(SCHEMA_NODE) && parameter.path(SCHEMA_NODE).has(EXAMPLES_NODE)) {
            JsonNode examplesNode = parameter.path(SCHEMA_NODE).path(EXAMPLES_NODE);

            if (examplesNode.isObject()) {
               Iterator<String> exampleNames = parameter.path(SCHEMA_NODE).path(EXAMPLES_NODE).fieldNames();

               while (exampleNames.hasNext()) {
                  String exampleName = exampleNames.next();
                  log.debug("Processing example {}", exampleName);

                  JsonNode example = parameter.path(SCHEMA_NODE).path(EXAMPLES_NODE).path(exampleName);
                  String exampleValue = getExampleValue(example);
                  log.debug("{} {} {}", parameterName, exampleName, exampleValue);

                  Map<String, String> exampleParams = results.computeIfAbsent(exampleName, k -> new HashMap<>());
                  exampleParams.put(parameterName, exampleValue);
               }
            } else if (examplesNode.isArray()) {
               Iterator<JsonNode> examples = examplesNode.elements();
               while (examples.hasNext()) {
                  JsonNode exampleNode = examples.next();

                  String exampleName = null;
                  String exampleValue = null;

                  // Try processing the "name:value" form
                  if (exampleNode.asText().contains(":")) {
                     String example = exampleNode.asText();
                     exampleName = example.substring(0, example.indexOf(":"));
                     exampleValue = example.substring(example.indexOf(":") + 1);
                  } else {
                     // Try processing the "name: > value: value" form
                     exampleName = exampleNode.fieldNames().next();
                     exampleValue = getExampleValue(exampleNode.fields().next().getValue());
                  }
                  log.debug("{} {} {}", parameterName, exampleName, exampleValue);

                  Map<String, String> exampleParams = results.computeIfAbsent(exampleName, k -> new HashMap<>());
                  exampleParams.put(parameterName, exampleValue);
               }
            }
         }
      }
      return results;
   }

   /**
    * Get the value of an example. This can be direct value field or those of followed $ref
    */
   private String getExampleValue(JsonNode example) {
      if (example.has(EXAMPLE_VALUE_NODE)) {
         JsonNode valueNode = followRefIfAny(example.path(EXAMPLE_VALUE_NODE));
         return getValueString(valueNode);
      }
      if (example.has("$ref")) {
         JsonNode component = followRefIfAny(example);
         return getExampleValue(component);
      }
      return null;
   }

   /** */
   private List<JsonNode> followRefsIfAny(JsonNode referencableNode) {
      List<JsonNode> results = new ArrayList<>();
      if (referencableNode.has("$ref")) {
         // Extract single reference.
         String ref = referencableNode.path("$ref").asText();
         results.add(rootSpecification.at(ref.substring(1)));
      } else {
         // Check for multi-structures.
         for (String structure : MULTI_STRUCTURES) {
            if (referencableNode.has(structure) && referencableNode.path(structure).isArray()) {
               ArrayNode arrayNode = (ArrayNode) referencableNode.path(structure);
               for (int i = 0; i < arrayNode.size(); i++) {
                  JsonNode structureNode = arrayNode.get(i);
                  results.add(followRefIfAny(structureNode));
               }
            }
         }
      }
      // If no reference found, put the node itself.
      if (results.isEmpty()) {
         results.add(referencableNode);
      }
      return results;
   }
}
