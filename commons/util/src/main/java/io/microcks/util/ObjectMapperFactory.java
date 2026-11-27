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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.util.unit.DataSize;
import org.yaml.snakeyaml.LoaderOptions;

/**
 * Utility class that allow instantiation of ObjectMapper objects using common parameters that can be overridden by the
 * application at configuration time.
 * @author laurent
 */
public class ObjectMapperFactory {

   /** Align default code point limit on SnakeYaml default one (3mb). */
   private static int codePointLimit = 3 * 1024 * 1024;

   // Utility classes should not have public constructors. Add a private one to hide the default public one.
   private ObjectMapperFactory() {
   }

   /**
    * Configure the maximum size of a parsed file (default is 3mb).
    * @param maxFileSize The size using standard Spring data unit expression
    */
   public static void configureMaxFileSize(String maxFileSize) {
      int codePointLimitCandidate = (int) DataSize.parse(maxFileSize).toBytes();
      // Only update of more than default value.
      if (codePointLimitCandidate > codePointLimit) {
         codePointLimit = codePointLimitCandidate;
      }
   }

   /** @return A Jackson ObjectMapper configured for JSON parsing with common settings. */
   public static ObjectMapper getJsonObjectMapper() {
      return new ObjectMapper();
   }

   /** @return A Jackson ObjectMapper configured for YAML parsing with common settings. */
   public static ObjectMapper getYamlObjectMapper() {
      LoaderOptions options = new LoaderOptions();
      options.setCodePointLimit(codePointLimit);
      YAMLFactory yamlFactory = YAMLFactory.builder().loaderOptions(options).build();
      return new ObjectMapper(yamlFactory);
   }
}
