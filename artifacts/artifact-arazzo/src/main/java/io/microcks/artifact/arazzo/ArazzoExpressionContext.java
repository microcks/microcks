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
 * What the runtime expressions of a step can see: the inputs of the workflow, the outputs of the steps before it and,
 * once it is answered, its response. The Arazzo minion sees the response a step received from the API it called;
 * Microcks sees the response its mocks gave to the application it verifies.
 * @author SebastienDegodez
 */
public interface ArazzoExpressionContext {

   /** @return The inputs of the workflow, by name */
   Map<String, Object> inputs();

   /** @return The outputs of the steps so far, by step id then output name */
   Map<String, Map<String, Object>> steps();

   /**
    * @param expression The expression that needs the response, to tell why it has no value
    * @return The status code of the response
    * @throws ArazzoExpressionException if the step has no response yet
    */
   int statusCode(String expression);

   /**
    * @param expression The expression that needs the response, to tell why it has no value
    * @return The headers of the response, by name, each with its values
    * @throws ArazzoExpressionException if the step has no response yet
    */
   Map<String, List<String>> responseHeaders(String expression);

   /**
    * @param expression The expression that needs the response, to tell why it has no value
    * @return The body of the response read as JSON, or as text when it is not JSON
    * @throws ArazzoExpressionException if the step has no response yet
    */
   Object responseBody(String expression);
}
