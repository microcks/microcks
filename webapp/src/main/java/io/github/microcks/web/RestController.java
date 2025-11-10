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
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.ParameterConstraintUtil;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.delay.DelaySpec;
import io.github.microcks.util.openapi.OpenAPISchemaValidator;
import io.github.microcks.util.openapi.OpenAPITestRunner;
import io.github.microcks.util.tracing.CommonAttributes;
import io.github.microcks.util.tracing.CommonEvents;
import io.github.microcks.util.tracing.TraceUtil;
import io.github.microcks.util.openapi.SwaggerSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A controller for mocking Rest responses.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
public class RestController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(RestController.class);

   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;
   private final RestInvocationProcessor invocationProcessor;

   @Value("${mocks.rest.enable-cors-policy}")
   private Boolean enableCorsPolicy;
   @Value("${mocks.rest.cors.allowedOrigins}")
   private String corsAllowedOrigins;
   @Value("${mocks.rest.cors.allowCredentials}")
   private Boolean corsAllowCredentials;

   @Value("${validation.resourceUrl}")
   private String validationResourceUrl;

   /**
    * Build a RestController with required dependencies.
    * @param serviceRepository   The repository to access services definitions
    * @param resourceRepository  The repository to access resources definitions
    * @param invocationProcessor The invocation processor to apply REST mocks dispatching logic
    */
   public RestController(ServiceRepository serviceRepository, ResourceRepository resourceRepository,
         RestInvocationProcessor invocationProcessor) {
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
      this.invocationProcessor = invocationProcessor;
   }

   @SuppressWarnings("java:S3752")
   @RequestMapping(value = "/rest/{service}/{version}/**", method = { RequestMethod.HEAD, RequestMethod.OPTIONS,
         RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
   public ResponseEntity<byte[]> execute(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @RequestParam(value = "delay", required = false) Long requestedDelay,
         @RequestParam(value = "delayStrategy", required = false) String requestedDelayStrategy,
         @RequestBody(required = false) String body, @RequestHeader HttpHeaders headers, HttpServletRequest request,
         HttpMethod method) {

      log.info("Servicing mock response for service [{}, {}] on uri {} with verb {}", serviceName, version,
            request.getRequestURI(), method);
      log.debug("Request body: {}", body);

      long startTime = System.currentTimeMillis();

      // Find matching service and operation.
      MockInvocationContext ic = findInvocationContext(serviceName, version, request, method);
      if (ic.service() == null) {
         return new ResponseEntity<>(
               String.format("The service %s with version %s does not exist!", serviceName, version).getBytes(),
               HttpStatus.NOT_FOUND);
      }

      // Check matching operation.
      if (ic.operation() == null) {
         // Handle OPTIONS request if CORS policy is enabled.
         if (Boolean.TRUE.equals(enableCorsPolicy) && HttpMethod.OPTIONS.equals(method)) {
            log.debug("No valid operation found but Microcks configured to apply CORS policy");
            return handleCorsRequest(request);
         }

         log.debug("No valid operation found and Microcks configured to not apply CORS policy...");
         return new ResponseEntity<>(HttpStatus.NOT_FOUND);
      }
      log.debug("Found a valid operation {} with rules: {}", ic.operation().getName(),
            ic.operation().getDispatcherRules());
      DelaySpec delay = MockControllerCommons.getDelay(headers, requestedDelay, requestedDelayStrategy);
      return processMockInvocationRequest(ic, startTime, delay, body, headers, request, method);
   }

   @SuppressWarnings("java:S3752")
   @RequestMapping(value = "/rest-valid/{service}/{version}/**", method = { RequestMethod.HEAD, RequestMethod.OPTIONS,
         RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
   public ResponseEntity<byte[]> validateAndExecute(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @RequestParam(value = "delay", required = false) Long requestedDelay,
         @RequestParam(value = "delayStrategy", required = false) String requestedDelayStrategy,
         @RequestBody(required = false) String body, @RequestHeader HttpHeaders headers, HttpServletRequest request,
         HttpMethod method) {

      log.info("Servicing mock response for service [{}, {}] on uri {} with verb {}", serviceName, version,
            request.getRequestURI(), method);
      log.debug("Request body: {}", body);

      long startTime = System.currentTimeMillis();

      // Find matching service and operation.
      MockInvocationContext ic = findInvocationContext(serviceName, version, request, method);
      if (ic.service() == null) {
         return new ResponseEntity<>(
               String.format("The service %s with version %s does not exist!", serviceName, version).getBytes(),
               HttpStatus.NOT_FOUND);
      }

      // Check matching operation.
      if (ic.operation() == null) {
         // Handle OPTIONS request if CORS policy is enabled.
         if (Boolean.TRUE.equals(enableCorsPolicy) && HttpMethod.OPTIONS.equals(method)) {
            log.debug("No valid operation found but Microcks configured to apply CORS policy");
            return handleCorsRequest(request);
         }

         log.debug("No valid operation found and Microcks configured to not apply CORS policy...");
         return new ResponseEntity<>(HttpStatus.NOT_FOUND);
      }
      log.debug("Found a valid operation {} with rules: {}", ic.operation().getName(),
            ic.operation().getDispatcherRules());

      // Try to validate request payload (body) if we have one.
      String shortContentType = getShortContentType(request.getContentType());
      if (body != null && !body.trim().isEmpty()
            && OpenAPITestRunner.APPLICATION_JSON_TYPES_PATTERN.matcher(shortContentType).matches()) {
         log.debug("Looking for an OpenAPI/Swagger schema to validate request body");

         Resource openapiSpecResource = findResourceCandidate(ic.service());
         if (openapiSpecResource == null) {
            return new ResponseEntity<>(
                  String.format("The service %s with version %s does not have an OpenAPI/Swagger schema!", serviceName,
                        version).getBytes(),
                  HttpStatus.PRECONDITION_FAILED);
         }

         List<String> errors = getErrors(body, ic, shortContentType, openapiSpecResource);

         log.debug("Schema validation errors: {}", errors.size());
         // Return a 400 http code with errors.
         if (!errors.isEmpty()) {
            return new ResponseEntity<>(errors.toString().getBytes(), HttpStatus.BAD_REQUEST);
         }
      }

      DelaySpec delay = MockControllerCommons.getDelay(headers, requestedDelay, requestedDelayStrategy);
      return processMockInvocationRequest(ic, startTime, delay, body, headers, request, method);
   }

   /** Get the errors from OpenAPI/Swagger schema validation. */
   private List<String> getErrors(String body, MockInvocationContext ic, String shortContentType,
         Resource openapiSpecResource) {
      boolean isOpenAPIv3 = !ResourceType.SWAGGER.equals(openapiSpecResource.getType());

      JsonNode openApiSpec = null;
      try {
         openApiSpec = OpenAPISchemaValidator.getJsonNodeForSchema(openapiSpecResource.getContent());
      } catch (IOException ioe) {
         log.debug("OpenAPI specification cannot be transformed into valid JsonNode schema, so failing");
      }

      // Extract JsonNode corresponding to operation.
      String verb = ic.operation().getName().split(" ")[0].toLowerCase();
      String path = ic.operation().getName().split(" ")[1].trim();

      // Get body content as a string.
      JsonNode contentNode = null;
      try {
         contentNode = OpenAPISchemaValidator.getJsonNode(body);
      } catch (IOException ioe) {
         log.debug("Response body cannot be accessed or transformed as Json, returning failure");
      }
      String jsonPointer = "/paths/" + path.replace("/", "~1") + "/" + verb + "/requestBody";

      List<String> errors = null;
      if (isOpenAPIv3) {
         errors = OpenAPISchemaValidator.validateJsonMessage(openApiSpec, contentNode, jsonPointer, shortContentType,
               validationResourceUrl);
      } else {
         errors = SwaggerSchemaValidator.validateJsonMessage(openApiSpec, contentNode, jsonPointer,
               validationResourceUrl);
      }
      return errors;
   }

   /** Process REST mock invocation. */
   private ResponseEntity<byte[]> processMockInvocationRequest(MockInvocationContext ic, long startTime,
         DelaySpec delay, String body, HttpHeaders headers, HttpServletRequest request, HttpMethod method) {

      Span span = Span.current();
      TraceUtil.enableExplainTracing();
      span.setAttribute(CommonAttributes.SERVICE_NAME, ic.service().getName());
      span.setAttribute(CommonAttributes.SERVICE_VERSION, ic.service().getVersion());
      span.setAttribute(CommonAttributes.OPERATION_NAME, ic.operation().getName());
      span.setAttribute(CommonAttributes.OPERATION_METHOD, ic.operation().getMethod());

      // Add an event for the invocation reception with a human-friendly message.
      span.addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(), TraceUtil
            .explainSpanEventBuilder(
                  String.format("Received REST invocation %s %s", ic.operation().getMethod(), ic.resourcePath()))
            .put(CommonAttributes.HTTP_METHOD, request.getMethod())
            .put(CommonAttributes.QUERY_STRING, request.getQueryString() != null ? request.getQueryString() : "empty")
            .put(CommonAttributes.BODY_SIZE, body != null ? body.length() : 0)
            .put(CommonAttributes.BODY_CONTENT,
                  body != null ? (body.length() > 1000 ? body.substring(0, 1000) + "..." : body) : "empty")
            .put(CommonAttributes.URI_FULL,
                  request.getRequestURL().toString()
                        + (request.getQueryString() != null ? "?" + request.getQueryString() : ""))
            .put(CommonAttributes.CLIENT_ADDRESS, request.getRemoteAddr()).build());

      String violationMsg = validateParameterConstraintsIfAny(ic.operation(), request);
      if (violationMsg != null) {
         // if a constraint is violated, add an event and return a 400 error.
         span.addEvent(CommonEvents.PARAMETER_CONSTRAINT_VIOLATED.getEventName(),
               TraceUtil.explainSpanEventBuilder(violationMsg).build());
         span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Parameter constraint violation");
         span.setAttribute(CommonAttributes.ERROR_STATUS, 400);

         return new ResponseEntity<>((violationMsg + ". Check parameter constraints.").getBytes(),
               HttpStatus.BAD_REQUEST);
      }

      ResponseResult response = invocationProcessor.processInvocation(ic, startTime, delay, body, headers, request);
      return new ResponseEntity<>(response.content(), response.headers(), response.status());
   }

   /** Find the invocation context for this mock request. */
   private MockInvocationContext findInvocationContext(String serviceName, String version, HttpServletRequest request,
         HttpMethod method) {
      // Extract resourcePath for matching with correct operation and build the encoded URI fragment to retrieve simple resourcePath.
      String serviceAndVersion = MockControllerCommons.composeServiceAndVersion(serviceName, version);
      String resourcePath = MockControllerCommons.extractResourcePath(request, serviceAndVersion);
      log.debug("Found resourcePath: {}", resourcePath);

      // If serviceName was encoded with '+' instead of '%20', remove them.
      if (serviceName.contains("+")) {
         serviceName = serviceName.replace('+', ' ');
      }

      // Find matching service.
      Service service = serviceRepository.findByNameAndVersion(serviceName, version);
      if (service == null) {
         return new MockInvocationContext(null, null, resourcePath);
      }

      // Find matching operation.
      Operation operation = findOperation(service, method, resourcePath);
      return new MockInvocationContext(service, operation, resourcePath);
   }

   @CheckForNull
   private Operation findOperation(Service service, HttpMethod method, String resourcePath) {
      // Remove trailing '/' if any.
      String trimmedResourcePath = trimResourcePath(resourcePath);

      Operation result = findOperationByResourcePath(service, method, resourcePath, trimmedResourcePath);

      if (result == null) {
         // We may not have found an Operation because of not exact resource path matching with an operation
         // using a Fallback dispatcher. Try again, just considering the verb and path pattern of operation.
         result = findOperationByPathPattern(service, method, resourcePath);
      }
      return result;
   }

   @CheckForNull
   private Operation findOperationByPathPattern(Service service, HttpMethod method, String resourcePath) {
      for (Operation operation : service.getOperations()) {
         // Select operation based onto Http verb (GET, POST, PUT, etc ...)
         // ... then check is current resource path matches operation path pattern.
         if (operation.getMethod().equals(method.name()) && operation.getResourcePaths() != null) {
            // Produce a matching regexp removing {part} and :part from pattern.
            String operationPattern = getURIPattern(operation.getName());
            //operationPattern = operationPattern.replaceAll("\\{.+\\}", "([^/])+");
            operationPattern = operationPattern.replaceAll("\\{[\\w-]+\\}", "([^/])+");
            operationPattern = operationPattern.replaceAll("(/:[^:^/]+)", "\\/([^/]+)");
            if (resourcePath.matches(operationPattern)) {
               return operation;
            }
         }
      }
      return null;
   }

   @CheckForNull
   private Operation findOperationByResourcePath(Service service, HttpMethod method, String resourcePath,
         String trimmedResourcePath) {
      for (Operation operation : service.getOperations()) {
         // Select operation based onto Http verb (GET, POST, PUT, etc ...)
         // ... then check is we have a matching resource path.
         if (operation.getMethod().equals(method.name()) && operation.getResourcePaths() != null
               && (operation.getResourcePaths().contains(resourcePath)
                     || operation.getResourcePaths().contains(trimmedResourcePath))) {
            return operation;
         }
         // Check by simple name comparison if no resource path is defined.
         if (operation.getName().equals(method.name().toUpperCase() + " " + resourcePath)) {
            return operation;
         }
      }
      return null;
   }

   /** Trim the resource path if it ends with a '/'. */
   private String trimResourcePath(String resourcePath) {
      if (resourcePath.endsWith("/")) {
         return resourcePath.substring(0, resourcePath.length() - 1);
      }
      return resourcePath;
   }

   /** Get the short content type without charset information. */
   private String getShortContentType(String contentType) {
      // Sanitize charset information from media-type.
      if (contentType != null && contentType.contains("charset=") && contentType.indexOf(";") >= 1) {
         return contentType.substring(0, contentType.indexOf(";"));
      }
      return contentType;
   }

   /** Validate the parameter constraints and return a single string with violation message if any. */
   private String validateParameterConstraintsIfAny(Operation rOperation, HttpServletRequest request) {
      if (rOperation.getParameterConstraints() != null) {
         for (ParameterConstraint constraint : rOperation.getParameterConstraints()) {
            String violationMsg = ParameterConstraintUtil.validateConstraint(request, constraint);
            if (violationMsg != null) {
               return violationMsg;
            }
         }
      }
      return null;
   }

   /** Retrieve URI Pattern from operation name (remove starting verb name). */
   private String getURIPattern(String operationName) {
      if (operationName.startsWith("GET ") || operationName.startsWith("POST ") || operationName.startsWith("PUT ")
            || operationName.startsWith("DELETE ") || operationName.startsWith("PATCH ")
            || operationName.startsWith("OPTIONS ")) {
         return operationName.substring(operationName.indexOf(' ') + 1);
      }
      return operationName;
   }

   /** Handle a CORS request putting the correct headers in response entity. */
   private ResponseEntity<byte[]> handleCorsRequest(HttpServletRequest request) {
      // Retrieve and set access control headers from those coming in request.
      List<String> accessControlHeaders = new ArrayList<>();
      Collections.list(request.getHeaders("Access-Control-Request-Headers")).forEach(accessControlHeaders::add);
      HttpHeaders requestHeaders = new HttpHeaders();
      requestHeaders.setAccessControlAllowHeaders(accessControlHeaders);
      requestHeaders.setAccessControlExposeHeaders(accessControlHeaders);

      // Apply CORS headers to response with 204 response code.
      return ResponseEntity.noContent().header("Access-Control-Allow-Origin", corsAllowedOrigins)
            .header("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE, PATCH").headers(requestHeaders)
            .header("Access-Allow-Credentials", String.valueOf(corsAllowCredentials))
            .header("Access-Control-Max-Age", "3600").header("Vary", "Accept-Encoding, Origin").build();
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
