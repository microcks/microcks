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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.util.test.HttpTestRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;

/**
 * An extension of AbstractTestRunner that deals with GraphQL calls.
 * @author laurent
 */
public class GraphQLTestRunner extends HttpTestRunner {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GraphQLTestRunner.class);

   /** Content-type for JSON that is the sole valid response type. */
   private static final String APPLICATION_JSON_TYPE = "application/json";

   private ObjectMapper mapper = new ObjectMapper();

   private ResourceRepository resourceRepository;

   private String lastQueryContent = null;
   private List<String> lastValidationErrors = null;

   /**
    * Build a new GraphQLTestRunner.
    * @param resourceRepository Access to resources repository
    */
   public GraphQLTestRunner(ResourceRepository resourceRepository) {
      this.resourceRepository = resourceRepository;
   }


   @Override
   protected void prepareRequest(Request request) {
      try {
         JsonNode requestNode = mapper.readTree(request.getContent());
         lastQueryContent = requestNode.path("query").asText();
      } catch (JsonProcessingException jpe) {
         log.error("JsonProcessingException while preparing GraphQL test query", jpe);
         lastQueryContent = "";
      }
   }

   @Override
   protected int extractTestReturnCode(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse, String responseContent) {

      int code = TestReturn.SUCCESS_CODE;

      int responseCode = 0;
      try {
         responseCode = httpResponse.getStatusCode().value();
         log.debug("Response status code : {}", responseCode);
      } catch (IOException ioe) {
         log.debug("IOException while getting raw status code in response", ioe);
         return TestReturn.FAILURE_CODE;
      }

      // Check that we're in the 20x family.
      if (!String.valueOf(responseCode).startsWith("20")) {
         log.debug("Response code if not in 20x range, assuming it's a failure");
         return TestReturn.FAILURE_CODE;
      }

      // Extract response content-type in any case.
      String contentType = null;
      if (httpResponse.getHeaders().getContentType() != null) {
         log.debug("Response media-type is {}", httpResponse.getHeaders().getContentType());
         contentType = httpResponse.getHeaders().getContentType().toString();
         // Sanitize charset information from media-type.
         if (contentType.contains("charset=") && contentType.indexOf(";") > 0) {
            contentType = contentType.substring(0, contentType.indexOf(";"));
         }
      }

      // Do not try to validate response content if no content provided ;-)
      // Also do not try to schema validate something that is not application/json for now...
      if (responseCode != 204 && APPLICATION_JSON_TYPE.equals(contentType)) {
         // Retrieve the resource corresponding to OpenAPI specification if any.
         Resource graphqlSchemaResource = null;
         List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(),
               ResourceType.GRAPHQL_SCHEMA);
         if (!resources.isEmpty()) {
            graphqlSchemaResource = resources.get(0);
         }
         if (graphqlSchemaResource == null) {
            log.debug("Found no GraphQL specification resource for service {}, so failing validating", service.getId());
            return TestReturn.FAILURE_CODE;
         }

         JsonNode responseSchema = null;
         try {
            responseSchema = GraphQLSchemaValidator.buildResponseJsonSchema(graphqlSchemaResource.getContent(),
                  lastQueryContent);
            log.debug("responseSchema: {}", responseSchema);
            lastValidationErrors = GraphQLSchemaValidator.validateJson(responseSchema,
                  mapper.readTree(responseContent));
         } catch (IOException ioe) {
            log.debug("Response body cannot be accessed or transformed as Json, returning failure");
            return TestReturn.FAILURE_CODE;
         }

         if (!lastValidationErrors.isEmpty()) {
            log.debug(
                  "GraphQL schema validation errors found: {}, marking test as failed." + lastValidationErrors.size());
            return TestReturn.FAILURE_CODE;
         }
         log.debug("GraphQL schema validation of response is successful !");
      }
      return code;
   }

   @Override
   protected String extractTestReturnMessage(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse) {
      StringBuilder builder = new StringBuilder();
      if (lastValidationErrors != null && !lastValidationErrors.isEmpty()) {
         for (String error : lastValidationErrors) {
            builder.append(error).append("/n");
         }
      }
      // Reset just after consumption so avoid side-effects.
      lastValidationErrors = null;
      return builder.toString();
   }

   /**
    * Build the HttpMethod corresponding to string.
    */
   @Override
   public HttpMethod buildMethod(String method) {
      return HttpMethod.POST;
   }
}
