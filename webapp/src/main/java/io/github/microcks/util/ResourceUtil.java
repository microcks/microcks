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

import io.github.microcks.domain.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.stream.Stream;

/**
 * Utility class to retrieve classpath resource and replace content in template resources (like OpenAPI, AsyncAPI spec
 * templates).
 * @author laurent
 */
public class ResourceUtil {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ResourceUtil.class);
   private static final String SERVICE_PATTERN = "\\{service\\}";
   private static final String VERSION_PATTERN = "\\{version\\}";
   private static final String RESOURCE_PATTERN = "\\{resource\\}";
   private static final String SCHEMA_PATTERN = "\\{resourceSchema\\}";

   private static final String REFERENCE_PATTERN = "\\{reference\\}";

   /**
    * Load a resource from classspath using its path.
    * @param resourcePath The path of resource to load.
    * @return The resource input stream
    * @throws IOException if resource cannot be found or opened
    */
   public static InputStream getClasspathResource(String resourcePath) throws IOException {
      Resource template = new ClassPathResource(resourcePath);
      return template.getInputStream();
   }

   /**
    * Given a resource stream holding placeholder patterns (aka {placeholder}), replace the patterns with actual value
    * coming from Service, an API resource name, an API schema and a reference payload.
    * @param stream           The stream to scan for patterns and substitute in.
    * @param service          The Service corresponding to API
    * @param resource         The API resource
    * @param referenceSchema  An optional reference API schema
    * @param referencePayload An optional reference resource payload
    * @return
    */
   public static String replaceTemplatesInSpecStream(InputStream stream, Service service, String resource,
         JsonNode referenceSchema, String referencePayload) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
      StringWriter writer = new StringWriter();

      try (Stream<String> lines = reader.lines()) {
         lines.map(line -> replaceInLine(line, service, resource, referenceSchema, referencePayload))
               .forEach(line -> writer.write(line + "\n"));
      }
      return writer.toString();
   }

   /** Do the replacement within a given stream line. */
   private static String replaceInLine(String line, Service service, String resource, JsonNode referenceSchema,
         String referencePayload) {
      line = line.replaceAll(SERVICE_PATTERN, service.getName());
      line = line.replaceAll(VERSION_PATTERN, service.getVersion());
      line = line.replaceAll(RESOURCE_PATTERN, resource);
      if (line.matches(".*" + SCHEMA_PATTERN + ".*")) {
         if (referenceSchema != null) {
            // Serialize reference schema and replace it.
            try {
               ObjectMapper mapper = new ObjectMapper(
                     new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                           .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES).disable(YAMLGenerator.Feature.INDENT_ARRAYS));
               String schema = mapper.writeValueAsString(referenceSchema);
               log.debug("schema: " + schema);
               line = line.replaceAll(SCHEMA_PATTERN, schema.replaceAll("\\n", "\n      "));
            } catch (Exception e) {
               log.warn("Exception while replacing resource schema", e);
            }
         } else {
            // Stick to the default: an empty type definition.
            line = line.replaceAll(SCHEMA_PATTERN, "");
         }
      }
      if (line.matches(".*" + REFERENCE_PATTERN + ".*")) {
         if (referencePayload != null) {
            // Inline Json and escape quotes.
            line = line.replaceAll(REFERENCE_PATTERN, referencePayload.replaceAll("\\n", ""));
         } else {
            // Stick to the default: an empty reference.
            line = line.replaceAll(REFERENCE_PATTERN, "");
         }
      }
      return line;
   }
}
