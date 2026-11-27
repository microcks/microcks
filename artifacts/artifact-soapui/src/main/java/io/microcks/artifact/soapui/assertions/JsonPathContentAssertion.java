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
package io.github.microcks.util.soapui.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

/**
 * An assertion that uses a Json path expression to extract content and check it's matching expected content.
 * @author laurent
 */
public class JsonPathContentAssertion extends WildcardMatchingAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(JsonPathContentAssertion.class);

   /** The JsonPath expression. Value is a string with a valid JsonPath expression. */
   public static final String PATH_PARAM = "path";

   /** The expected content param. Value is a string that may contain wildcards if `allowWildcards` is set to true. */
   public static final String EXPECTED_CONTENT_PARAM = "content";


   private String path;

   private String expectedContent;

   private String errorMessage;


   @Override
   public void configure(Map<String, String> configParams) {
      super.configure(configParams);
      path = configParams.get(PATH_PARAM);
      expectedContent = configParams.get(EXPECTED_CONTENT_PARAM);
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting JsonPath on {}, expecting: {}", path, expectedContent);

      if (path == null) {
         errorMessage = "Missing path for JsonPath assertion";
         return AssertionStatus.FAILED;
      }
      if (expectedContent == null) {
         errorMessage = "Missing content for JsonPath assertion";
         return AssertionStatus.FAILED;
      }

      try {
         // Parse json text ang get root node.
         ObjectMapper mapper = new ObjectMapper();
         JsonNode rootNode = mapper.readTree(new StringReader(exchange.responseContent()));

         // Retrieve evaluated node within JSON tree.
         JsonNode evaluatedNode = rootNode.at(path);
         String result = evaluatedNode.asText();

         if (allowWildcards) {
            if (!isSimilar(expectedContent, result)) {
               errorMessage = "Comparison failed for path [" + path + "], expecting [" + expectedContent
                     + "], actual was [" + result + "]";
               return AssertionStatus.FAILED;
            }
         } else {
            if (!expectedContent.equals(result)) {
               errorMessage = "Comparison failed for path [" + path + "], expecting [" + expectedContent
                     + "], actual was [" + result + "]";
               return AssertionStatus.FAILED;
            }
         }
      } catch (Exception e) {
         log.warn("Exception while compiling/evaluating JsonPointer", e);
         errorMessage = "Exception while compiling/evaluating JoinPointer: " + path;
         return AssertionStatus.FAILED;
      }
      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return List.of(errorMessage);
   }
}
