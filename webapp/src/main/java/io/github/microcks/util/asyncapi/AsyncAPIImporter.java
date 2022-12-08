/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util.asyncapi;

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.BindingType;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of MockRepositoryImporter that deals with AsyncAPI v2.0.x specification
 * file ; whether encoding into JSON or YAML documents.
 * @author laurent
 */
public class AsyncAPIImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(AsyncAPIImporter.class);

   private static final String[] MULTI_STRUCTURES = {
         "allOf", "anyOf", "oneOf"
   };

   private boolean isYaml = true;
   private JsonNode spec;
   private String specContent;
   private ReferenceResolver referenceResolver;

   private static final List<String> VALID_VERBS = Arrays.asList("subscribe", "publish");

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local AsyncAPI spec file
    * @param referenceResolver An optional resolver for references present into the AsyncAPI file
    * @throws IOException if project file cannot be found or read.
    */
   public AsyncAPIImporter(String specificationFilePath, ReferenceResolver referenceResolver) throws IOException {
      this.referenceResolver = referenceResolver;
      try {
         // Analyse first lines of file content to guess repository type.
         String line = null;
         BufferedReader reader = Files.newBufferedReader(new File(specificationFilePath).toPath(), Charset.forName("UTF-8"));
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Check is we start with json object or array definition.
            if (line.startsWith("{") || line.startsWith("[")) {
               isYaml = false;
               break;
            }
            else if (line.startsWith("---") || line.startsWith("-") || line.startsWith("asyncapi: ")) {
               isYaml = true;
               break;
            }
         }
         reader.close();

         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(specificationFilePath));
         specContent = new String(bytes, Charset.forName("UTF-8"));
         // Convert them to Node using Jackson object mapper.
         ObjectMapper mapper = null;
         if (isYaml) {
            mapper = new ObjectMapper(new YAMLFactory());
            // Jackson YAML parser can't deal with any quotes around "$ref" and double quotes around the path.
            specContent = specContent.replaceAll("[\\\"']?\\$ref[\\\"']?:\\s*[\\\"'](#.*)[\\\"']", "\\$ref: '$1'")
                  .replaceAll("[\\\"']?pattern[\\\"']?:\\s*[\\\"'](.*)[\\\"']", "pattern: $1");
         } else {
            mapper = new ObjectMapper();
         }
         spec = mapper.readTree(specContent.getBytes(Charset.forName("UTF-8")));
      } catch (Exception e) {
         log.error("Exception while parsing AsyncAPI specification file " + specificationFilePath, e);
         throw new IOException("AsyncAPI spec file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      service.setName(spec.path("info").path("title").asText());
      service.setVersion(spec.path("info").path("version").asText());
      service.setType(ServiceType.EVENT);

      // Complete metadata if specified via extension.
      if (spec.path("info").has(MetadataExtensions.MICROCKS_EXTENSION)) {
         Metadata metadata = new Metadata();
         MetadataExtractor.completeMetadata(metadata, spec.path("info").path(MetadataExtensions.MICROCKS_EXTENSION));
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

      // Build a suitable name.
      String name = service.getName() + "-" + service.getVersion();
      if (isYaml) {
         name += ".yaml";
      } else {
         name += ".json";
      }

      // Build a brand new resource just with spec content.
      Resource resource = new Resource();
      resource.setName(name);
      resource.setType(ResourceType.ASYNC_API_SPEC);
      resource.setContent(specContent);
      results.add(resource);

      // Browser operations messages and message traits to see if we have
      // references for external schemas only if we have a resolver available.
      if (referenceResolver != null) {
         for (Operation operation : service.getOperations()) {
            String[] operationElements = operation.getName().split(" ");
            String messageNamePtr = "/channels/" + operationElements[1].replaceAll("/", "~1");
            messageNamePtr += "/" + operationElements[0].toLowerCase() + "/message";

            JsonNode messageNode = spec.at(messageNamePtr);
            if (messageNode != null) {
               // If it's a $ref, then navigate to it.
               messageNode = followRefIfAny(messageNode);

               // Extract payload schema here.
               if (messageNode.has("payload")) {
                  JsonNode payloadNode = messageNode.path("payload");

                  // Check we have a reference that is not a local one.
                  if (payloadNode.has("$ref")
                        && !payloadNode.path("$ref").asText().startsWith("#")) {
                     String ref = payloadNode.path("$ref").asText();

                     // Remove trailing anchor marker (we may have this in Avro schema
                     // to indicate exact Resource)
                     if (ref.contains("#")) {
                        ref = ref.substring(0, ref.indexOf("#"));
                     }

                     try {
                        // Extract content using resolver.
                        String content = referenceResolver.getHttpReferenceContent(ref, "UTF-8");

                        // Build a new resource from content. Use the escaped operation path.
                        Resource schemaResource = new Resource();
                        schemaResource.setName(IdBuilder.buildResourceFullName(service, operation));
                        schemaResource.setPath(ref);
                        schemaResource.setContent(content);

                        // We have to look at schema format to know the type.
                        // Default value is set at first.
                        String schemaFormat = "application/vnd.aai.asyncapi";
                        if (messageNode.has("schemaFormat")) {
                           schemaFormat = messageNode.path("schemaFormat").asText();
                        }

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
                        results.add(schemaResource);
                     } catch (IOException ioe) {
                        log.error("IOException while trying to resolve reference " + ref, ioe);
                        log.info("Ignoring the reference {} cause it could not be resolved", ref);
                     }
                  }
               }
            }
         }

         // Finally try to clean up resolved references and associated resources (files)
         referenceResolver.cleanResolvedReferences();
      }
      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException {
      List<Exchange> result = new ArrayList<>();

      // Retrieve default content type, defaulting to application/json.
      String defaultContentType = "application/json";
      if (spec.has("defaultContentType")) {
         defaultContentType = spec.get("defaultContentType").asText("application/json");
      }

      // Iterate on specification "channels" nodes.
      Iterator<Entry<String, JsonNode>> channels = spec.path("channels").fields();
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

               // If it's a $ref or multi-structure (oneOf, anyOf, allOf), then navigate to them.
               List<JsonNode> messageBodies = followRefsIfAny(messageBody);

               for (JsonNode extractedMsgBody : messageBodies) {
                  // Get message content type.
                  String contentType = defaultContentType;
                  if (extractedMsgBody.has("contentType")) {
                     contentType = extractedMsgBody.path("contentType").asText();
                  }
                  // No need to go further if no examples.
                  if (extractedMsgBody.has("examples")) {
                     Iterator<JsonNode> examples = extractedMsgBody.path("examples").elements();
                     int exampleIndex = 0;
                     while (examples.hasNext()) {
                        JsonNode exampleNode = examples.next();

                        EventMessage eventMessage = null;
                        if (exampleNode.has("name")) {
                           // As of AsyncAPI 2.1.0 () we can now have a 'name' property for examples!
                           eventMessage = extractFromAsyncAPI21Example(contentType, exampleNode);
                        } else if (exampleNode.has("payload")) {
                           // As of https://github.com/microcks/microcks/issues/385, we should support the restriction
                           // coming from AsyncAPI GItHub master revision and associated tooling...
                           eventMessage = extractFromAsyncAPIExample(contentType, exampleNode, channelName.trim() + "-" + exampleIndex);
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
                              eventMessage.setDispatchCriteria(DispatchCriteriaHelper.buildFromPartsMap(parts));
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
   private List<Operation> extractOperations() throws MockRepositoryImportException {
      List<Operation> results = new ArrayList<>();

      // Iterate on specification "channels" nodes.
      Iterator<Entry<String, JsonNode>> channels = spec.path("channels").fields();
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
               if (channel.getValue().has("bindings")) {
                  Iterator<String> bindingNames = channel.getValue().path("bindings").fieldNames();
                  while (bindingNames.hasNext()) {
                     String bindingName = bindingNames.next();
                     JsonNode binding = channel.getValue().path("bindings").path(bindingName);

                     switch (bindingName) {
                        case "ws":
                           Binding b = retrieveOrInitOperationBinding(operation, BindingType.WS);
                           if (binding.has("method")) {
                              b.setMethod(binding.path("method").asText());
                           }
                           break;
                        case "amqp":
                           b = retrieveOrInitOperationBinding(operation, BindingType.AMQP);
                           if (binding.has("is")) {
                              String is = binding.path("is").asText();
                              if ("queue".equals(is)) {
                                 b.setDestinationType("queue");
                                 JsonNode queue = binding.get("queue");
                                 b.setDestinationName(queue.get("name").asText());
                              } else if ("routingKey".equals(is)) {
                                 JsonNode exchange = binding.get("exchange");
                                 b.setDestinationType(exchange.get("type").asText());
                              }
                           }
                           break;
                        case "googlepubsub":
                           b = retrieveOrInitOperationBinding(operation, BindingType.GOOGLEPUBSUB);
                           if (binding.has("topic")) {
                              b.setDestinationName(binding.get("topic").asText());
                           }
                           if (binding.has("messageRetentionDuration")) {
                              b.setPersistent(true);
                           }
                           break;
                     }
                  }
               }

               // Then look for bindings at the operation level.
               if (verb.getValue().has("bindings")) {
                  Iterator<String> bindingNames = verb.getValue().path("bindings").fieldNames();
                  while (bindingNames.hasNext()) {
                     String bindingName = bindingNames.next();
                     JsonNode binding = verb.getValue().path("bindings").path(bindingName);

                     switch (bindingName) {
                        case "kafka":
                           break;
                        case "mqtt":
                           Binding b = retrieveOrInitOperationBinding(operation, BindingType.MQTT);
                           if (binding.has("qos")) {
                              b.setQoS(binding.path("qos").asText());
                           }
                           if (binding.has("retain")) {
                              b.setPersistent(binding.path("retain").asBoolean());
                           }
                           break;
                        case "amqp1":
                           b = retrieveOrInitOperationBinding(operation, BindingType.AMQP1);
                           if (binding.has("destinationName")) {
                              b.setDestinationName(binding.path("destinationName").asText());
                           }
                           if (binding.has("destinationType")) {
                              b.setDestinationType(binding.path("destinationType").asText());
                           }
                           break;
                     }
                  }
               }

               // Then look for bindings at the message level.
               JsonNode messageBody = verb.getValue().path("message");
               messageBody = followRefIfAny(messageBody);
               if (messageBody.has("bindings")) {
                  Iterator<String> bindingNames = messageBody.path("bindings").fieldNames();
                  while (bindingNames.hasNext()) {
                     String bindingName = bindingNames.next();
                     JsonNode binding = messageBody.path("bindings").path(bindingName);

                     switch (bindingName) {
                        case "kafka":
                           Binding b = retrieveOrInitOperationBinding(operation, BindingType.KAFKA);
                           if (binding.has("key")) {
                              b.setKeyType(binding.path("key").path("type").asText());
                           }
                           break;
                        case "mqtt":
                        case "amqp1":
                           break;
                     }
                  }
               }

               results.add(operation);
            }
         }
      }

      return results;
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
      List<Header> headers = getExampleHeaders(exampleNode);
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
      List<Header> headers = getExampleHeaders(exampleNode);
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
         if (example.has("payload")) {
            String exampleValue = getExamplePayload(example);

            // Build and store a request object.
            eventMessage = new EventMessage();
            eventMessage.setName(exampleName);
            eventMessage.setContent(exampleValue);
            eventMessage.setMediaType(contentType);

            // Now complete with specified headers.
            List<Header> headers = getExampleHeaders(example);
            for (Header header : headers) {
               eventMessage.addHeader(header);
            }
         }
      }
      return eventMessage;
   }

   /** Extract the list of Header from an example node. */
   private List<Header> getExampleHeaders(JsonNode example) {
      List<Header> results = new ArrayList<>();

      if (example.has("headers")) {
         Iterator<Entry<String, JsonNode>> headers = null;

         if (example.path("headers").getNodeType() == JsonNodeType.OBJECT ) {
            headers = example.path("headers").fields();
         } else if (example.path("headers").getNodeType() == JsonNodeType.STRING) {
            // Try to parse string as a JSON Object...
            try {
               ObjectMapper mapper = new ObjectMapper();
               JsonNode headersNode = mapper.readTree(example.path("headers").asText());
               headers = headersNode.fields();

            } catch (Exception e) {
               log.warn("Headers value {} is a string but not JSON, skipping it", example.path("headers").asText());
            }
         }

         if (headers != null) {
            while (headers.hasNext()) {
               Entry<String, JsonNode> property = headers.next();
               String propertyName = property.getKey();

               Header header = new Header();
               header.setName(property.getKey());
               // Values may be multiple and CSV.
               Set<String> headerValues = Arrays.stream(property.getValue().asText().split(","))
                     .map(value -> value.trim())
                     .collect(Collectors.toSet());
               header.setValues(headerValues);
               results.add(header);
            }
         }
      }
      return results;
   }

   /** Get the value of an example. This can be direct value field or those of followed $ref. */
   private String getExamplePayload(JsonNode example) {
      if (example.has("payload")) {
         if (example.path("payload").getNodeType() == JsonNodeType.ARRAY ||
               example.path("payload").getNodeType() == JsonNodeType.OBJECT ) {
            return example.path("payload").toString();
         }
         return example.path("payload").asText();
      }
      if (example.has("$payloadRef")) {
         // $ref: '#/components/examples/param_laurent'
         String ref = example.path("$payloadRef").asText();
         JsonNode component = spec.at(ref.substring(1));
         return getExamplePayload(component);
      }
      return null;
   }

   /** Check variables parts presence into given channel. */
   private static boolean channelHasParts(String channel) {
      return (channel.indexOf("/{") != -1);
   }

   /**
    * Extract parameters within a channel node and organize them by example.
    * Key of value map is param name. Value of value map is param value ;-)
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

         if (parameter.has("schema") && parameter.path("schema").has("examples")) {
            Iterator<String> exampleNames = parameter.path("schema").path("examples").fieldNames();

            while (exampleNames.hasNext()) {
               String exampleName = exampleNames.next();
               log.debug("Processing example {}", exampleName);

               JsonNode example = parameter.path("schema").path("examples").path(exampleName);
               String exampleValue = getExampleValue(example);
               log.debug("{} {} {}", parameterName, exampleName, exampleValue);

               Map<String, String> exampleParams = results.get(exampleName);
               if (exampleParams == null) {
                  exampleParams = new HashMap<>();
                  results.put(exampleName, exampleParams);
               }
               exampleParams.put(parameterName, exampleValue);
            }
         }
      }
      return results;
   }

   /** Get the value of an example. This can be direct value field or those of followed $ref */
   private String getExampleValue(JsonNode example) {
      if (example.has("value")) {
         if (example.path("value").getNodeType() == JsonNodeType.ARRAY ||
               example.path("value").getNodeType() == JsonNodeType.OBJECT) {
            return example.path("value").toString();
         }
         return example.path("value").asText();
      }
      if (example.has("$ref")) {
         // $ref: '#/components/examples/param_laurent'
         JsonNode component = followRefIfAny(example);
         return getExampleValue(component);
      }
      return null;
   }

   /** */
   private JsonNode followRefIfAny(JsonNode referencableNode) {
      if (referencableNode.has("$ref")) {
         String ref = referencableNode.path("$ref").asText();
         return spec.at(ref.substring(1));
      }
      return referencableNode;
   }

   /** */
   private List<JsonNode> followRefsIfAny(JsonNode referencableNode) {
      List<JsonNode> results = new ArrayList<>();
      if (referencableNode.has("$ref")) {
         // Extract single reference.
         String ref = referencableNode.path("$ref").asText();
         results.add(spec.at(ref.substring(1)));
      } else {
         // Check for multi-structures.
         for (String structure : MULTI_STRUCTURES) {
            if (referencableNode.has(structure) && referencableNode.path(structure).isArray()) {
               ArrayNode arrayNode = (ArrayNode) referencableNode.path(structure);
               for (int i=0; i<arrayNode.size(); i++) {
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

   /** */
   private static Binding retrieveOrInitOperationBinding(Operation operation, BindingType type) {
      Binding binding = null;
      if (operation.getBindings() != null) {
         binding = operation.getBindings().get(type);
      }
      if (binding == null) {
         binding = new Binding(type);
         operation.addBinding(type.toString(), binding);
      }
      return binding;
   }
}
