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
package io.github.microcks.util;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An abstract class that may be used as a base for implementing MockRepositoryImporter that are using
 * JSON/YAML file as a repository. It provides utility methods for handling references and loading of external
 * resources, resolution of JSON pointers and so on...
 * @author laurent
 */
public abstract class AbstractJsonRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(AbstractJsonRepositoryImporter.class);

   protected Boolean isYaml;
   protected JsonNode rootSpecification;

   protected String rootSpecificationContent;
   protected ReferenceResolver referenceResolver;
   protected List<Resource> externalResources = new ArrayList<>();
   protected Map<Resource, JsonNode> externalResourcesContent = new HashMap<>();

   /**
    * Build a new importer using the path of specification file and an optional reference resolver.
    * @param specificationFilePath The path to local JSON spec file
    * @param referenceResolver An optional resolver for references present into the OpenAPI file
    * @throws IOException if JSON spec file cannot be found or read.
    */
   protected AbstractJsonRepositoryImporter(String specificationFilePath, ReferenceResolver referenceResolver) throws IOException {
      this.referenceResolver = referenceResolver;
      BufferedReader reader = null;
      try {
         // Analyse first lines of file content to guess repository type.
         String line = null;
         reader = Files.newBufferedReader(new File(specificationFilePath).toPath(), StandardCharsets.UTF_8);
         while ((line = reader.readLine()) != null && isYaml == null) {
            line = line.trim();
            // Check is we start with json object or array definition.
            if (line.startsWith("{") || line.startsWith("[")) {
               isYaml = false;
            }  else if (line.startsWith("---") || line.startsWith("-")
                  || line.startsWith("openapi: ") || line.startsWith("asyncapi: ")) {
               isYaml = true;
            }
         }

         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(specificationFilePath));
         rootSpecificationContent = new String(bytes, StandardCharsets.UTF_8);

         // Convert them to Node using Jackson object mapper.
         ObjectMapper mapper = getObjectMapper(isYaml);
         rootSpecification = mapper.readTree(rootSpecificationContent.getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
         log.error("Exception while parsing JSON specification file " + specificationFilePath, e);
         throw new IOException("JSON spec file parsing error");
      } finally {
         if (reader != null) {
            reader.close();
         }
      }
   }

   /**
    * Given a service, initialize all external references that may be found via
    * `$ref` nodes pointing to absolute or relative pointers. This has a the side effect
    * to initialize the `externalResources` member.
    * @param service
    */
   protected void initializeExternalReferences(Service service) {
      if (referenceResolver != null) {
         Map<String, String> referenceBases = new HashMap<>();
         Set<String> references = findAllExternalRefs(rootSpecification);
         references.forEach(ref -> referenceBases.put(ref, referenceResolver.getBaseRepositoryUrl()));

         while (!references.isEmpty()) {
            String ref = references.stream().findFirst().orElseThrow();
            System.err.println("Ref is " + ref);
            try {
               referenceResolver.setBaseRepositoryUrl(referenceBases.get(ref));

               // Extract content using resolver.
               String content = referenceResolver.getHttpReferenceContent(ref, StandardCharsets.UTF_8);
               String resourceName = ref.substring(ref.lastIndexOf('/') + 1);
               System.err.println("Creating a resource with resourceName: " + resourceName);

               // Build a new resource from content. Use the escaped operation path.
               Resource schemaResource = new Resource();
               schemaResource.setName(IdBuilder.buildResourceFullName(service, resourceName));
               schemaResource.setPath(ref);
               schemaResource.setContent(content);
               schemaResource.setType(ResourceType.JSON_SCHEMA);

               // Now go down the resource content and resolve its embedded references.
               // Also update the references bases to track the root url for this ref in order
               ObjectMapper mapper = getObjectMapper(!ref.endsWith(".json"));
               JsonNode resourceRootSpecification = mapper.readTree(content);
               Set<String> embeddedReferences = findAllExternalRefs(resourceRootSpecification);
               references.addAll(embeddedReferences);
               embeddedReferences.forEach(embeddedRef ->
                  referenceBases.put(embeddedRef, referenceResolver.getReferenceURL(ref))
               );

               if (!ref.startsWith("http")) {
                  // If a relative resource, replace with new name.
                  rootSpecificationContent = rootSpecificationContent.replace(ref, URLEncoder.encode(schemaResource.getName(), StandardCharsets.UTF_8));
               }
               externalResources.add(schemaResource);
            } catch (IOException ioe) {
               log.error("IOException while trying to resolve reference {}", ref, ioe);
               log.info("Ignoring the reference {} cause it could not be resolved", ref);
            }
            // Remove ref as it has been processed.
            references.remove(ref);
         }
         // Finally try to clean up resolved references and associated resources (files)
         referenceResolver.cleanResolvedReferences();
      }
   }

   /** Follow the $ref if we have one. Otherwise, return given node. */
   protected JsonNode followRefIfAny(JsonNode referencableNode) {
      if (referencableNode.has("$ref")) {
         String ref = referencableNode.path("$ref").asText();
         return getNodeForRef(ref);
      }
      return referencableNode;
   }

   /** Get the string representation of a node in spec. */
   protected String getValueString(JsonNode valueNode) {
      // Get string representation if array or object.
      if (valueNode.getNodeType() == JsonNodeType.ARRAY || valueNode.getNodeType() == JsonNodeType.OBJECT) {
         return valueNode.toString();
      }
      // Else get raw representation.
      return valueNode.asText();
   }

   /** Get appropriate Yaml or Json object mapper. */
   private ObjectMapper getObjectMapper(boolean isYaml) {
      return isYaml ? ObjectMapperFactory.getYamlObjectMapper() : ObjectMapperFactory.getJsonObjectMapper();
   }

   /** Browse Json node to extract references and store them into externalRefs. */
   private Set<String> findAllExternalRefs(JsonNode node) {
      Set<String> externalRefs = new HashSet<>();
      // If node has a $ref child, it's a stop condition.
      if (node.has("$ref")) {
         String ref = node.path("$ref").asText();
         if (!ref.startsWith("#")) {
            // Our ref could be used for examples and be something like './weather-examples.json#/0'.
            String filePath = ref;
            if (ref.contains("#/")) {
               filePath = ref.substring(0, ref.indexOf("#/"));
            }
            externalRefs.add(filePath);
         }
      } else {
         // Iterate on all other children.
         Iterator<JsonNode> children = node.elements();
         while (children.hasNext()) {
            externalRefs.addAll(findAllExternalRefs(children.next()));
         }
      }
      return externalRefs;
   }

   /** Get the JsonNode for reference within the specification. */
   private JsonNode getNodeForRef(String reference) {
      if (reference.startsWith("#/")) {
         return rootSpecification.at(reference.substring(1));
      }
      return getNodeForExternalRef(reference);
   }

   /** Get the JsonNode for reference that is localed in external resource. */
   private JsonNode getNodeForExternalRef(String externalReference) {
      String path = externalReference;

      // We may have a Json pointer to a specific place in external reference.
      String pointerInFile = null;
      if (externalReference.indexOf("#/") != -1) {
         path = externalReference.substring(0, externalReference.indexOf("#/"));
         pointerInFile = externalReference.substring(externalReference.indexOf("#/"));
      }

      for (Resource resource : externalResources) {
         if (path.equals(resource.getPath())) {
            JsonNode resourceNode = externalResourcesContent.computeIfAbsent(resource, k -> {
                  try {
                     return ObjectMapperFactory.getYamlObjectMapper().readTree(resource.getContent());
                  } catch (JsonProcessingException e) {
                     throw new JsonRepositoryParsingException("Get a JSON processing exception on " + externalReference, e);
                  }
            });
            if (pointerInFile != null) {
               return resourceNode.at(pointerInFile.substring(1));
            }
            return resourceNode;
         }
      }
      log.warn("Found no resource for reference {}", externalReference);
      return null;
   }

   /** Custom runtime exception for Json repository parsing errors. */
   public class JsonRepositoryParsingException extends RuntimeException {
      public JsonRepositoryParsingException(String message) {
         super(message);
      }
      public JsonRepositoryParsingException(String message, Throwable cause) {
         super(message, cause);
      }
   }
}
