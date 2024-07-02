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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.util.test.HttpTestRunner;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * This is an implementation of HttpTestRunner that deals with OpenAPI schema validation. This implementation now
 * manages the 2 flavors of OpenAPI: OpenAPI v3.x and Swagger v2.x.
 * @author laurent
 */
public class OpenAPITestRunner extends HttpTestRunner {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenAPITestRunner.class);

   /** Content-types for JSON that are valid response types validated using JSON Schemas. */
   public static final Pattern APPLICATION_JSON_TYPES_PATTERN = Pattern.compile("application/.*json");

   private final ResourceRepository resourceRepository;
   private final ResponseRepository responseRepository;

   private final boolean validateResponseCode;


   /** The URL of resources used for validation. */
   private String resourceUrl = null;

   private List<String> lastValidationErrors = null;

   /**
    * Build a new OpenAPITestRunner.
    * @param resourceRepository   Access to resources repository
    * @param responseRepository   Access to response repository
    * @param validateResponseCode whether to validate response code
    */
   public OpenAPITestRunner(ResourceRepository resourceRepository, ResponseRepository responseRepository,
         boolean validateResponseCode) {
      this.resourceRepository = resourceRepository;
      this.responseRepository = responseRepository;
      this.validateResponseCode = validateResponseCode;
   }

   /**
    * The URL of resources used for validation.
    * @return The URL of resources used for validation
    */
   public String getResourceUrl() {
      return resourceUrl;
   }

   /**
    * The URL of resources used for validation.
    * @param resourceUrl The URL of resources used for validation.
    */
   public void setResourceUrl(String resourceUrl) {
      this.resourceUrl = resourceUrl;
   }

   /**
    * Build the HttpMethod corresponding to string.
    */
   @Override
   public HttpMethod buildMethod(String method) {
      return HttpMethod.valueOf(method.toUpperCase());
   }

   @Override
   protected int extractTestReturnCode(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse, String responseContent) {
      int code = TestReturn.SUCCESS_CODE;

      int responseCode = 0;
      try {
         responseCode = httpResponse.getStatusCode().value();
         log.debug("Response status code: {}", responseCode);
      } catch (IOException ioe) {
         log.debug("IOException while getting raw status code in response", ioe);
         return TestReturn.FAILURE_CODE;
      }

      // Extract response content-type in any case.
      String contentType = null;
      if (httpResponse.getHeaders().getContentType() != null) {
         contentType = httpResponse.getHeaders().getContentType().toString();
         log.debug("Response media-type is {}", httpResponse.getHeaders().getContentType());
      }

      // If required, compare response code and content-type to expected ones.
      if (validateResponseCode) {
         Response expectedResponse = responseRepository.findById(request.getResponseId()).orElse(null);
         if (expectedResponse != null) {
            log.debug("Response expected status code: {}", expectedResponse.getStatus());
            if (!String.valueOf(responseCode).equals(expectedResponse.getStatus())) {
               log.debug("Response HttpStatus does not match expected one, returning failure");
               lastValidationErrors = List
                     .of(String.format("Response HttpStatus does not match expected one. Expecting %s but got %d",
                           expectedResponse.getStatus(), responseCode));
               return TestReturn.FAILURE_CODE;
            }

            if (expectedResponse.getMediaType() != null
                  && !contentTypesAreEquivalent(expectedResponse.getMediaType(), contentType)) {
               log.debug("Response Content-Type does not match expected one, returning failure");
               lastValidationErrors = List
                     .of(String.format("Response Content-Type does not match expected one. Expecting %s but got %s",
                           expectedResponse.getMediaType(), contentType));
               return TestReturn.FAILURE_CODE;
            }
         }
      }

      // Do not try to validate response content if no content provided ;-)
      // Also do not try to schema validate something that is not application/.*json for now...
      // Alternatives schemes are on their way for OpenAPI but not yet ready (see https://github.com/OAI/OpenAPI-Specification/pull/1736)
      String shortContentType = getShortContentType(contentType);
      if (responseCode != 204 && shortContentType != null
            && APPLICATION_JSON_TYPES_PATTERN.matcher(shortContentType).matches()) {

         boolean isOpenAPIv3 = true;

         // Retrieve the resource corresponding to OpenAPI specification if any.
         Resource openapiSpecResource = findResourceCandidate(service);
         if (openapiSpecResource == null) {
            log.debug("Found no OpenAPI specification resource for service {} - {}, so failing validating",
                  service.getId(), service.getName());
            return TestReturn.FAILURE_CODE;
         }

         // Check the type so guess the kind of validation.
         if (ResourceType.SWAGGER.equals(openapiSpecResource.getType())) {
            isOpenAPIv3 = false;
         }

         JsonNode openApiSpec = null;
         try {
            openApiSpec = OpenAPISchemaValidator.getJsonNodeForSchema(openapiSpecResource.getContent());
         } catch (IOException ioe) {
            log.debug("OpenAPI specification cannot be transformed into valid JsonNode schema, so failing");
            return TestReturn.FAILURE_CODE;
         }

         // Extract JsonNode corresponding to response.
         String verb = operation.getName().split(" ")[0].toLowerCase();
         String path = operation.getName().split(" ")[1].trim();

         // Get body content as a string.
         JsonNode contentNode = null;
         try {
            contentNode = OpenAPISchemaValidator.getJsonNode(responseContent);
         } catch (IOException ioe) {
            log.debug("Response body cannot be accessed or transformed as Json, returning failure");
            return TestReturn.FAILURE_CODE;
         }
         String jsonPointer = "/paths/" + path.replace("/", "~1") + "/" + verb + "/responses/" + responseCode;

         if (isOpenAPIv3) {
            lastValidationErrors = OpenAPISchemaValidator.validateJsonMessage(openApiSpec, contentNode, jsonPointer,
                  contentType, resourceUrl);
         } else {
            lastValidationErrors = SwaggerSchemaValidator.validateJsonMessage(openApiSpec, contentNode, jsonPointer,
                  resourceUrl);
         }

         if (!lastValidationErrors.isEmpty()) {
            log.debug("OpenAPI schema validation errors found {}, marking test as failed.",
                  lastValidationErrors.size());
            return TestReturn.FAILURE_CODE;
         }
         log.debug("OpenAPI schema validation of response is successful !");
      }
      return code;
   }

   @Override
   protected String extractTestReturnMessage(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse) {
      StringBuilder builder = new StringBuilder();
      if (lastValidationErrors != null && !lastValidationErrors.isEmpty()) {
         for (String error : lastValidationErrors) {
            builder.append(error).append('\n');
         }
      }
      // Reset just after consumption so avoid side-effects.
      lastValidationErrors = null;
      return builder.toString();
   }

   private boolean contentTypesAreEquivalent(String expectedContentType, String responseContentType) {
      if (responseContentType != null) {
         // If charset is specified, compare ignore case ignoring spaces.
         if (expectedContentType.contains("charset=")) {
            return expectedContentType.replace(" ", "").equalsIgnoreCase(responseContentType.replace(" ", ""));
         }
         // Sanitize charset information from media-type.
         String shortResponseContentType = getShortContentType(responseContentType);
         return expectedContentType.equalsIgnoreCase(shortResponseContentType);
      }
      return false;
   }

   private String getShortContentType(String contentType) {
      // Sanitize charset information from media-type.
      if (contentType != null && contentType.contains("charset=") && contentType.indexOf(";") > 0) {
         return contentType.substring(0, contentType.indexOf(";"));
      }
      return contentType;
   }

   private Resource findResourceCandidate(Service service) {
      Optional<Resource> candidate = Optional.empty();
      // Try resources marked within mainArtifact first.
      List<Resource> resources = resourceRepository.findMainByServiceId(service.getId());
      if (!resources.isEmpty()) {
         candidate = getResourceCandidate(resources);
      }
      // Else try all the services resources...
      if (candidate.isEmpty()) {
         resources = resourceRepository.findByServiceId(service.getId());
         if (!resources.isEmpty()) {
            candidate = getResourceCandidate(resources);
         }
      }
      return candidate.orElse(null);
   }

   private Optional<Resource> getResourceCandidate(List<Resource> resources) {
      return resources.stream()
            .filter(r -> ResourceType.OPEN_API_SPEC.equals(r.getType()) || ResourceType.SWAGGER.equals(r.getType()))
            .findFirst();
   }
}
