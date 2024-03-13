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
package io.github.microcks.util.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.regex.Pattern;

/**
 * This utility class evaluates JSON against one or more evaluation specifications. Specification may be represented
 * that way and use to find a suitable response for an incoming JSON request: <br>
 * 
 * <pre>
 * <code> {
 *   "exp": "/country",							# JSONPointer expression
 *   "operator": "equals",
 *   "cases": {
 *     "Belgium": "OK Created Response",        # Name of a Response for Belgium
 *     "Germany": "Forbidden Country Response", # Name of a Response for Germany
 *     "default": "Why not Response"			   # Name of default Response
 *   }
 * }</code>
 * </pre>
 * 
 * @author laurent
 */
public class JsonExpressionEvaluator {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(JsonExpressionEvaluator.class);

   /**
    * Evaluate a Json payload regarding a specification. Basically, it checks if payload conforms to the given
    * expression and then fond the suitable cases from within specification.
    * @param jsonText      The Json payload to evaluate
    * @param specification The evaluation specification (JSONPointer expression + operator + cases)
    * @return The result of evaluation is whether one of the cases, whether
    * @throws JsonMappingException if incoming Json payload is malformed or invalid
    */
   public static String evaluate(String jsonText, JsonEvaluationSpecification specification)
         throws JsonMappingException {
      // Parse json text ang get root node.
      JsonNode rootNode;
      try {
         ObjectMapper mapper = new ObjectMapper();
         rootNode = mapper.readTree(new StringReader(jsonText));
      } catch (Exception e) {
         log.error("Exception while parsing Json text", e);
         throw new JsonMappingException("Exception while parsing Json payload");
      }

      // Retrieve evaluated node within JSON tree.
      JsonNode evaluatedNode = rootNode.at(specification.getExp());
      String caseKey = evaluatedNode.asText();

      switch (specification.getOperator()) {
         case equals:
            // Consider simple equality.
            String value = specification.getCases().get(caseKey);
            return (value != null ? value : specification.getCases().getDefault());

         case range:
            // Consider range evaluation.
            double caseNumber = 0.000;
            try {
               caseNumber = Double.parseDouble(caseKey);
            } catch (NumberFormatException nfe) {
               log.error(caseKey + " into range expression cannot be parsed as number. Considering default case.");
               return specification.getCases().getDefault();
            }
            return foundRangeMatchingCase(caseNumber, specification.getCases());

         case regexp:
            // Consider regular expression evaluation for each case key.
            for (String choiceKey : specification.getCases().keySet()) {
               if (!"default".equals(choiceKey)) {
                  if (Pattern.matches(choiceKey, caseKey)) {
                     return specification.getCases().get(choiceKey);
                  }
               }
            }
            break;

         case size:
            // Consider size evaluation.
            if (evaluatedNode.isArray()) {
               int size = evaluatedNode.size();
               return foundRangeMatchingCase(size, specification.getCases());
            }
            break;

         case presence:
            // Consider presence evaluation of evaluatedNode directly.
            if (evaluatedNode != null && evaluatedNode.toString().length() > 0) {
               if (specification.getCases().containsKey("found")) {
                  return specification.getCases().get("found");
               }
            } else {
               if (specification.getCases().containsKey("missing")) {
                  return specification.getCases().get("missing");
               }
            }
            break;
      }
      return specification.getCases().getDefault();
   }

   /** Evaluate cases from dispatchCases against caseNumber, considering each key as a range expression. */
   private static String foundRangeMatchingCase(double caseNumber, DispatchCases dispatchCases) {
      // Evaluating each case key.
      for (String choiceKey : dispatchCases.keySet()) {
         boolean match = false;
         if (isValidRangeKey(choiceKey)) {
            try {
               double[] minAndMax = extractMinAndMaxFromRange(choiceKey);
               // Fastidious part... Apply operators depending of brackets orientations.
               if (choiceKey.startsWith("[") && choiceKey.endsWith("]")) {
                  if (caseNumber >= minAndMax[0] && caseNumber <= minAndMax[1]) {
                     match = true;
                  }
               } else if (choiceKey.startsWith("]") && choiceKey.endsWith("]")) {
                  if (caseNumber > minAndMax[0] && caseNumber <= minAndMax[1]) {
                     match = true;
                  }
               } else if (choiceKey.startsWith("[") && choiceKey.endsWith("[")) {
                  if (caseNumber >= minAndMax[0] && caseNumber < minAndMax[1]) {
                     match = true;
                  }
               } else if (choiceKey.startsWith("]") && choiceKey.endsWith("[")) {
                  if (caseNumber > minAndMax[0] && caseNumber < minAndMax[1]) {
                     match = true;
                  }
               }
            } catch (NumberFormatException nfe) {
               log.warn(choiceKey + " expression cannot be parsed as number for min and max range.");
            }
         }
         if (match) {
            return dispatchCases.get(choiceKey);
         }
      }
      return dispatchCases.getDefault();
   }

   /** Validate key is a correct range expression. */
   private static boolean isValidRangeKey(String key) {
      boolean hasCorrectStart = key.startsWith("[") || key.startsWith("]");
      boolean hasCorrectEnd = key.endsWith("[") || key.endsWith("]");
      boolean hasDelimiter = key.contains(";");
      return hasCorrectStart && hasDelimiter && hasCorrectEnd;
   }

   /** Extract minimum and maximum from range expression. Considering min on the left side and max on the right side. */
   private static double[] extractMinAndMaxFromRange(String range) throws NumberFormatException {
      double[] results = new double[2];
      String[] minAndMax = range.split(";");
      results[0] = Double.parseDouble(minAndMax[0].substring(1));
      results[1] = Double.parseDouble(minAndMax[1].substring(0, minAndMax[1].length() - 1));
      return results;
   }
}
