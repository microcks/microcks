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
package io.github.microcks.config;

import graphql.parser.ParserOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to set up GraphQL parser options. This configuration allows to set the maximum number of
 * characters and tokens for the GraphQL parser. The values are read from application properties.
 * @author laurent
 */
@Configuration
public class GraphQLParserConfiguration {

   @Value("${graphql.parser.max-characters:#{null}}")
   private Integer maxCharacters;

   @Value("${graphql.parser.max-tokens:#{null}}")
   private Integer maxTokens;

   @PostConstruct
   public void configureGraphQLParser() {
      // Override the default ParserOptions with the ones defined in application.properties.
      if (maxCharacters != null) {
         ParserOptions.setDefaultParserOptions(
               ParserOptions.getDefaultParserOptions().transform(opts -> opts.maxCharacters(maxCharacters)));
      }
      if (maxTokens != null) {
         ParserOptions.setDefaultParserOptions(
               ParserOptions.getDefaultParserOptions().transform(opts -> opts.maxTokens(maxTokens)));
      }
   }
}
