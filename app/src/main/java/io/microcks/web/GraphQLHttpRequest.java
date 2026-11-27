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
package io.github.microcks.util.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A wrapper for parsing GraphQL requests over Http.
 * @author laurent
 */
public class GraphQLHttpRequest {

   String query;
   String operationName;
   JsonNode variables;

   public String getQuery() {
      return query;
   }

   public String getOperationName() {
      return operationName;
   }

   public JsonNode getVariables() {
      return variables;
   }

   /**
    * Build a GraphQLHttpRequest from Http servlet request and body content.
    * @param body    The content of Http request.
    * @param request The servlet request
    * @return The wrapper object
    * @throws Exception if request is no valid Json as expected by GraphQL over Http spec.
    */
   public static GraphQLHttpRequest from(String body, HttpServletRequest request) throws Exception {
      // We'll need a Json mapper.
      ObjectMapper mapper = new ObjectMapper();
      GraphQLHttpRequest parameters = new GraphQLHttpRequest();

      if ("POST".equalsIgnoreCase(request.getMethod())) {
         // If it's a post, everything is in body.
         JsonNode json = mapper.readTree(body);
         parameters.query = json.path("query").asText();
         parameters.operationName = json.path("operationName").asText(null);
         parameters.variables = json.get("variables");
      } else {
         // If it's a get, we're using parameters.
         parameters.query = request.getParameter("query");
         parameters.operationName = request.getParameter("operationName");
         parameters.variables = getVariables(mapper, request.getParameter("variables"));
      }
      return parameters;
   }

   /**
    * Build a simple GraphQLHttpRequest with a query and variables.
    * @param operationName Is used as both query and operationName.
    * @param variables     The variables to use for the query.
    * @return The wrapper object
    */
   public static GraphQLHttpRequest from(String operationName, JsonNode variables) {
      GraphQLHttpRequest parameters = new GraphQLHttpRequest();
      parameters.query = operationName;
      parameters.operationName = operationName;
      parameters.variables = variables;
      return parameters;
   }

   private static JsonNode getVariables(ObjectMapper mapper, String variables) throws Exception {
      if (variables != null) {
         return mapper.readTree(variables);
      }
      return null;
   }
}
