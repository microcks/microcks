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
package io.github.microcks.util.postman;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.PatternSyntaxException;

/**
 * Some utility functions/methods for dealing with Postman specific formatting.
 * @author laurent
 */
public class PostmanUtil {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(PostmanUtil.class);

   /** Regular expression used to evaluate operation name matching. */
   private static final String OPERATION_NAME_EXPRESSION_PREFIX = "(GET|POST|PUT|PATCH|DELETE|OPTION)?( *)(/)?";

   /** Private constructor to hide the default one. */
   private PostmanUtil() {
   }

   /**
    * Tells if 2 operations may be equivalent giving their names. Useful when comparing OpenAPI operations (containing
    * <code>{param}</code> in path) and Postman operations (containing <code>:param</code> in path).
    * @param operationNameRef       Reference operation name (typically the one coming from OpenAPI)
    * @param operationNameCandidate Candidate operatoin name (typically the one coming from Postman Collection)
    * @return True if both are equivalent, false otherwise.
    */
   public static boolean areOperationsEquivalent(String operationNameRef, String operationNameCandidate) {
      // First check equals ignoring case.
      if (operationNameRef.equalsIgnoreCase(operationNameCandidate)) {
         return true;
      }
      // Then we may have an OpenAPI template we should convert to Postman and check again.
      if (operationNameRef.contains("/{")) {
         String transformedName = operationNameRef.replaceAll("/\\{", "/:").replace("}", "");
         if (transformedName.equalsIgnoreCase(operationNameCandidate)) {
            return true;
         }
      }

      try {
         // Finally check again adding a verb as prefix.
         return operationNameCandidate.matches(OPERATION_NAME_EXPRESSION_PREFIX + operationNameRef);
      } catch (PatternSyntaxException pse) {
         log.debug("{}{} throws a PatternSyntaxException", OPERATION_NAME_EXPRESSION_PREFIX, operationNameRef);
      }
      return false;
   }
}
