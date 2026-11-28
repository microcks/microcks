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
package io.microcks.artifact.arazzo;

import java.util.List;
import java.util.Map;

/**
 * Evaluates the runtime expressions of Arazzo that Microcks supports: {@code $statusCode}, {@code $response.body},
 * {@code $response.header.<name>}, {@code $inputs.<name>} and {@code $steps.<stepId>.outputs.<name>}, the last four
 * followed by an optional {@code #<JSON pointer>}.
 * @author SebastienDegodez
 */
public final class ArazzoExpressionEvaluator {

   private static final String STATUS_CODE = "$statusCode";
   private static final String RESPONSE_BODY = "$response.body";
   private static final String RESPONSE_HEADER = "$response.header.";
   private static final String INPUTS = "$inputs.";
   private static final String STEPS = "$steps.";
   private static final String OUTPUTS = ".outputs.";

   /**
    * @param value   A value of the workflow: a runtime expression when it starts with $, else a constant
    * @param context What the expression can see
    * @return The value of the expression, or the constant
    */
   public Object resolve(String value, ArazzoExpressionContext context) {
      return isExpression(value) ? evaluate(value, context) : value;
   }

   /**
    * @param value A value of the workflow
    * @return Whether it is a runtime expression rather than a constant
    */
   public static boolean isExpression(String value) {
      return value.startsWith("$");
   }

   /**
    * @param expression A runtime expression
    * @param context    What the expression can see
    * @return Its value
    * @throws ArazzoExpressionException if the expression has no value
    */
   public Object evaluate(String expression, ArazzoExpressionContext context) {
      String[] parts = expression.split("#", 2);
      Object value = base(parts[0], context);
      return parts.length == 2 ? ArazzoJsonPointer.get(value, parts[1], parts[0]) : value;
   }

   private static Object base(String expression, ArazzoExpressionContext context) {
      if (expression.equals(STATUS_CODE)) {
         return context.statusCode(expression);
      }
      if (expression.equals(RESPONSE_BODY)) {
         return context.responseBody(expression);
      }
      if (expression.startsWith(RESPONSE_HEADER)) {
         return header(context.responseHeaders(expression), expression.substring(RESPONSE_HEADER.length()));
      }
      if (expression.startsWith(INPUTS)) {
         return lookup(context.inputs(), expression.substring(INPUTS.length()), "Unknown input: ");
      }
      int outputs = expression.indexOf(OUTPUTS);
      if (expression.startsWith(STEPS) && outputs > STEPS.length()) {
         String stepId = expression.substring(STEPS.length(), outputs);
         return lookup(lookup(context.steps(), stepId, "Unknown step: "),
               expression.substring(outputs + OUTPUTS.length()), "Unknown output of step " + stepId + ": ");
      }
      throw new ArazzoExpressionException("Unsupported runtime expression: " + expression);
   }

   private static String header(Map<String, List<String>> headers, String name) {
      return headers.entrySet().stream().filter(header -> header.getKey().equalsIgnoreCase(name))
            .map(header -> header.getValue().get(0)).findFirst()
            .orElseThrow(() -> new ArazzoExpressionException("No response header " + name));
   }

   private static <T> T lookup(Map<String, T> values, String name, String unknown) {
      if (!values.containsKey(name)) {
         throw new ArazzoExpressionException(unknown + name);
      }
      return values.get(name);
   }
}
