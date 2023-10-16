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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represent the specification of a Json payload evaluation. <code>exp</code> should represent a valid JSONPointer
 * expression, <code>operator</code> is an evaluation operator found into <code>EvaluationOperator</code> and
 * <code>cases</code> represents the different possible results of evaluation (along with a default choice).
 * @author laurent
 */
@JsonPropertyOrder({ "exp", "operator", "cases" })
public class JsonEvaluationSpecification {

   private String exp;
   private EvaluationOperator operator;
   private DispatchCases cases;

   public String getExp() {
      return exp;
   }

   public void setExp(String exp) {
      this.exp = exp;
   }

   public EvaluationOperator getOperator() {
      return operator;
   }

   public void setOperator(EvaluationOperator operator) {
      this.operator = operator;
   }

   public DispatchCases getCases() {
      return cases;
   }

   public void setCases(DispatchCases cases) {
      this.cases = cases;
   }

   /**
    * Build a specification from a JSON string.
    * @param jsonPayload The JSON payload representing valid specification
    * @return A newly build JsonEvaluationSpecification
    * @throws JsonMappingException if given JSON string cannot be parsed as a JsonEvaluationSpecification
    */
   public static JsonEvaluationSpecification buildFromJsonString(String jsonPayload) throws JsonMappingException {
      JsonEvaluationSpecification specification = null;
      try {
         ObjectMapper mapper = new ObjectMapper();
         specification = mapper.readValue(jsonPayload, JsonEvaluationSpecification.class);
      } catch (Exception e) {
         throw new JsonMappingException("Given JSON string cannot be interpreted as valid JsonEvaluationSpecification");
      }
      return specification;
   }
}
