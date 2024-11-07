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

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.ParameterLocation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.repository.ServiceStateRepository;
import io.github.microcks.service.ProxyService;
import io.github.microcks.util.AbsoluteUrlMatcher;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.ParameterConstraintUtil;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.JsonExpressionEvaluator;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.openapi.OpenAPISchemaValidator;
import io.github.microcks.util.openapi.OpenAPITestRunner;
import io.github.microcks.util.openapi.SwaggerSchemaValidator;
import io.github.microcks.util.script.ScriptEngineBinder;
import io.github.microcks.service.ServiceStateStore;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;

import javax.annotation.CheckForNull;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A controller for mocking Rest responses.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
public class RestController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(RestController.class);

   private final ServiceRepository serviceRepository;
   private final ServiceStateRepository serviceStateRepository;
   private final ResponseRepository responseRepository;
   private final ResourceRepository resourceRepository;

   private final ApplicationContext applicationContext;

   private final ProxyService proxyService;

   @Value("${mocks.enable-invocation-stats}")
   private Boolean enableInvocationStats;
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
    * @param serviceRepository      The repository to access services definitions
    * @param serviceStateRepository The repository to access service state
    * @param responseRepository     The repository to access responses definitions
    * @param resourceRepository     The repository to access resources definitions
    * @param applicationContext     The Spring application context
    * @param proxyService           The proxy to external URLs or services
    */
   public RestController(ServiceRepository serviceRepository, ServiceStateRepository serviceStateRepository,
         ResponseRepository responseRepository, ResourceRepository resourceRepository,
         ApplicationContext applicationContext, ProxyService proxyService) {
      this.serviceRepository = serviceRepository;
      this.serviceStateRepository = serviceStateRepository;
      this.responseRepository = responseRepository;
      this.resourceRepository = resourceRepository;
      this.applicationContext = applicationContext;
      this.proxyService = proxyService;
   }


   @RequestMapping(value = "/rest/{service}/{version}/**", method = { RequestMethod.HEAD, RequestMethod.OPTIONS,
         RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
   public ResponseEntity<byte[]> execute(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @RequestParam(value = "delay", required = false) Long delay,
         @RequestBody(required = false) String body, @RequestHeader HttpHeaders headers, HttpServletRequest request,
         HttpMethod method) {

      log.info("Servicing mock response for service [{}, {}] on uri {} with verb {}", serviceName, version,
            request.getRequestURI(), method);
      log.debug("Request body: {}", body);

      long startTime = System.currentTimeMillis();

      // Find matching service and operation.
      MockInvocationContext ic = findInvocationContext(serviceName, version, request, method);
      if (ic.service == null) {
         return new ResponseEntity<>(
               String.format("The service %s with version %s does not exist!", serviceName, version).getBytes(),
               HttpStatus.NOT_FOUND);
      }

      // Check matching operation.
      if (ic.operation == null) {
         // Handle OPTIONS request if CORS policy is enabled.
         if (Boolean.TRUE.equals(enableCorsPolicy) && HttpMethod.OPTIONS.equals(method)) {
            log.debug("No valid operation found but Microcks configured to apply CORS policy");
            return handleCorsRequest(request);
         }

         log.debug("No valid operation found and Microcks configured to not apply CORS policy...");
         return new ResponseEntity<>(HttpStatus.NOT_FOUND);
      }
      log.debug("Found a valid operation {} with rules: {}", ic.operation.getName(), ic.operation.getDispatcherRules());

      return processMockInvocationRequest(ic, startTime, delay, body, headers, request, method);
   }

   @RequestMapping(value = "/rest-valid/{service}/{version}/**", method = { RequestMethod.HEAD, RequestMethod.OPTIONS,
         RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
   public ResponseEntity<byte[]> validateAndExecute(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @RequestParam(value = "delay", required = false) Long delay,
         @RequestBody(required = false) String body, @RequestHeader HttpHeaders headers, HttpServletRequest request,
         HttpMethod method) {

      log.info("Servicing mock response for service [{}, {}] on uri {} with verb {}", serviceName, version,
            request.getRequestURI(), method);
      log.debug("Request body: {}", body);

      long startTime = System.currentTimeMillis();

      // Find matching service and operation.
      MockInvocationContext ic = findInvocationContext(serviceName, version, request, method);
      if (ic.service == null) {
         return new ResponseEntity<>(
               String.format("The service %s with version %s does not exist!", serviceName, version).getBytes(),
               HttpStatus.NOT_FOUND);
      }

      // Check matching operation.
      if (ic.operation == null) {
         // Handle OPTIONS request if CORS policy is enabled.
         if (Boolean.TRUE.equals(enableCorsPolicy) && HttpMethod.OPTIONS.equals(method)) {
            log.debug("No valid operation found but Microcks configured to apply CORS policy");
            return handleCorsRequest(request);
         }

         log.debug("No valid operation found and Microcks configured to not apply CORS policy...");
         return new ResponseEntity<>(HttpStatus.NOT_FOUND);
      }
      log.debug("Found a valid operation {} with rules: {}", ic.operation.getName(), ic.operation.getDispatcherRules());

      // Try to validate request payload (bnody) if we have one.
      String shortContentType = getShortContentType(request.getContentType());
      if (body != null && !body.trim().isEmpty()
            && OpenAPITestRunner.APPLICATION_JSON_TYPES_PATTERN.matcher(shortContentType).matches()) {
         log.debug("Looking for an OpenAPI/Swagger schema to validate request body");

         Resource openapiSpecResource = findResourceCandidate(ic.service);
         if (openapiSpecResource == null) {
            return new ResponseEntity<>(
                  String.format("The service %s with version %s does not have an OpenAPI/Swagger schema!", serviceName,
                        version).getBytes(),
                  HttpStatus.PRECONDITION_FAILED);
         }

         boolean isOpenAPIv3 = !ResourceType.SWAGGER.equals(openapiSpecResource.getType());

         JsonNode openApiSpec = null;
         try {
            openApiSpec = OpenAPISchemaValidator.getJsonNodeForSchema(openapiSpecResource.getContent());
         } catch (IOException ioe) {
            log.debug("OpenAPI specification cannot be transformed into valid JsonNode schema, so failing");
         }

         // Extract JsonNode corresponding to operation.
         String verb = ic.operation.getName().split(" ")[0].toLowerCase();
         String path = ic.operation.getName().split(" ")[1].trim();

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

         log.debug("Schema validation errors: {}", errors.size());
         // Return a 400 http code with errors.
         if (!errors.isEmpty()) {
            return new ResponseEntity<>(errors.toString().getBytes(), HttpStatus.BAD_REQUEST);
         }
      }

      return processMockInvocationRequest(ic, startTime, delay, body, headers, request, method);
   }

   /** Process REST mock invocation. */
   private ResponseEntity<byte[]> processMockInvocationRequest(MockInvocationContext ic, long startTime, Long delay,
         String body, HttpHeaders headers, HttpServletRequest request, HttpMethod method) {

      String violationMsg = validateParameterConstraintsIfAny(ic.operation, request);
      if (violationMsg != null) {
         return new ResponseEntity<>((violationMsg + ". Check parameter constraints.").getBytes(),
               HttpStatus.BAD_REQUEST);
      }

      // We must find dispatcher and its rules. Default to operation ones but
      // if we have a Fallback or Proxy-Fallback this is the one who is holding the first pass rules.
      String dispatcher = ic.operation.getDispatcher();
      String dispatcherRules = ic.operation.getDispatcherRules();
      FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(ic.operation);
      if (fallback != null) {
         dispatcher = fallback.getDispatcher();
         dispatcherRules = fallback.getDispatcherRules();
      }
      ProxyFallbackSpecification proxyFallback = MockControllerCommons.getProxyFallbackIfAny(ic.operation);
      if (proxyFallback != null) {
         dispatcher = proxyFallback.getDispatcher();
         dispatcherRules = proxyFallback.getDispatcherRules();
      }

      //
      DispatchContext dispatchContext = computeDispatchCriteria(ic.service, dispatcher, dispatcherRules,
            getURIPattern(ic.operation.getName()), UriUtils.decode(ic.resourcePath, "UTF-8"), request, body);
      log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());

      Response response = null;

      // Filter depending on requested media type.
      // TODO: validate dispatchCriteria with dispatcherRules
      List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
            IdBuilder.buildOperationId(ic.service, ic.operation), dispatchContext.dispatchCriteria());
      response = getResponseByMediaType(responses, request);

      if (response == null) {
         // When using the SCRIPT or JSON_BODY dispatchers, return of evaluation may be the name of response.
         responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(ic.service, ic.operation),
               dispatchContext.dispatchCriteria());
         response = getResponseByMediaType(responses, request);
      }

      if (response == null && fallback != null) {
         // If we've found nothing and got a fallback, that's the moment!
         responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(ic.service, ic.operation),
               fallback.getFallback());
         response = getResponseByMediaType(responses, request);
      }

      Optional<URI> proxyUrl = MockControllerCommons.getProxyUrlIfProxyIsNeeded(dispatcher, dispatcherRules,
            ic.resourcePath, proxyFallback, request, response);
      if (proxyUrl.isPresent()) {
         // If we've got a proxyUrl, that's the moment!
         return proxyService.callExternal(proxyUrl.get(), method, headers, body);
      }

      if (response == null) {
         if (dispatcher == null) {
            // In case no response found because dispatcher is null, just get one for the operation.
            // This will allow also OPTIONS operations (like pre-flight requests) with no dispatch criteria to work.
            log.debug("No responses found so far, tempting with just bare operationId...");
            responses = responseRepository.findByOperationId(IdBuilder.buildOperationId(ic.service, ic.operation));
            if (!responses.isEmpty()) {
               response = getResponseByMediaType(responses, request);
            }
         } else {
            // There is a dispatcher but we found no response => return 400 as per #819 and #1132.
            return new ResponseEntity<>(
                  String.format("The response %s does not exist!", dispatchContext.dispatchCriteria()).getBytes(),
                  HttpStatus.BAD_REQUEST);
         }
      }

      if (response != null) {
         HttpStatus status = (response.getStatus() != null ? HttpStatus.valueOf(Integer.parseInt(response.getStatus()))
               : HttpStatus.OK);

         // Deal with specific headers (content-type and redirect directive).
         HttpHeaders responseHeaders = new HttpHeaders();
         if (response.getMediaType() != null) {
            responseHeaders.setContentType(MediaType.valueOf(response.getMediaType() + ";charset=UTF-8"));
         }

         // Deal with headers from parameter constraints if any?
         recopyHeadersFromParameterConstraints(ic.operation, request, responseHeaders);

         // Adding other generic headers (caching directives and so on...)
         if (response.getHeaders() != null) {
            // First check if they should be rendered.
            EvaluableRequest evaluableRequest = MockControllerCommons.buildEvaluableRequest(body, ic.resourcePath,
                  request);
            Set<Header> renderedHeaders = MockControllerCommons.renderResponseHeaders(evaluableRequest,
                  dispatchContext.requestContext(), response);

            for (Header renderedHeader : renderedHeaders) {
               if ("Location".equals(renderedHeader.getName())) {
                  String location = renderedHeader.getValues().iterator().next();
                  if (!AbsoluteUrlMatcher.matches(location)) {
                     // We should process location in order to make relative URI specified an absolute one from
                     // the client perspective.
                     location = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                           + request.getContextPath() + "/rest" + MockControllerCommons
                                 .composeServiceAndVersion(ic.service.getName(), ic.service.getVersion())
                           + location;
                  }
                  responseHeaders.add(renderedHeader.getName(), location);
               } else {
                  if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(renderedHeader.getName())) {
                     responseHeaders.put(renderedHeader.getName(), new ArrayList<>(renderedHeader.getValues()));
                  }
               }
            }
         }

         // Render response content before waiting and returning.
         String responseContent = MockControllerCommons.renderResponseContent(body, ic.resourcePath, request,
               dispatchContext.requestContext(), response);

         // Setting delay to default one if not set.
         if (delay == null && ic.operation.getDefaultDelay() != null) {
            delay = ic.operation.getDefaultDelay();
         }
         MockControllerCommons.waitForDelay(startTime, delay);

         // Publish an invocation event before returning if enabled.
         if (Boolean.TRUE.equals(enableInvocationStats)) {
            MockControllerCommons.publishMockInvocation(applicationContext, this, ic.service, response, startTime);
         }

         // Return response content or just headers.
         if (responseContent != null) {
            return new ResponseEntity<>(responseContent.getBytes(StandardCharsets.UTF_8), responseHeaders, status);
         }
         return new ResponseEntity<>(responseHeaders, status);
      }

      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
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
      Operation result = null;

      // Remove trailing '/' if any.
      String trimmedResourcePath = resourcePath;
      if (trimmedResourcePath.endsWith("/")) {
         trimmedResourcePath = resourcePath.substring(0, resourcePath.length() - 1);
      }

      for (Operation operation : service.getOperations()) {
         // Select operation based onto Http verb (GET, POST, PUT, etc ...)
         if (operation.getMethod().equals(method.name())) {
            // ... then check is we have a matching resource path.
            if (operation.getResourcePaths() != null && (operation.getResourcePaths().contains(resourcePath)
                  || operation.getResourcePaths().contains(trimmedResourcePath))) {
               result = operation;
               break;
            }
         }
      }

      // We may not have found an Operation because of not exact resource path matching with an operation
      // using a Fallback dispatcher. Try again, just considering the verb and path pattern of operation.
      if (result == null) {
         for (Operation operation : service.getOperations()) {
            // Select operation based onto Http verb (GET, POST, PUT, etc ...)
            if (operation.getMethod().equals(method.name())) {
               // ... then check is current resource path matches operation path pattern.
               if (operation.getResourcePaths() != null) {
                  // Produce a matching regexp removing {part} and :part from pattern.
                  String operationPattern = getURIPattern(operation.getName());
                  //operationPattern = operationPattern.replaceAll("\\{.+\\}", "([^/])+");
                  operationPattern = operationPattern.replaceAll("\\{[\\w-]+\\}", "([^/])+");
                  operationPattern = operationPattern.replaceAll("(/:[^:^/]+)", "\\/([^/]+)");
                  if (resourcePath.matches(operationPattern)) {
                     result = operation;
                     break;
                  }
               }
            }
         }
      }
      return result;
   }


   /** Get the short content type without charset information. */
   private String getShortContentType(String contentType) {
      // Sanitize charset information from media-type.
      if (contentType != null && contentType.contains("charset=") && contentType.indexOf(";") > 0) {
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

   /** Compute a dispatch context with a dispatchCriteria string from type, rules and request elements. */
   private DispatchContext computeDispatchCriteria(Service service, String dispatcher, String dispatcherRules,
         String uriPattern, String resourcePath, HttpServletRequest request, String body) {
      String dispatchCriteria = null;
      Map<String, Object> requestContext = null;

      // Depending on dispatcher, evaluate request with rules.
      if (dispatcher != null) {
         switch (dispatcher) {
            case DispatchStyles.SEQUENCE:
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(dispatcherRules, uriPattern,
                     resourcePath);
               break;
            case DispatchStyles.SCRIPT:
               ScriptEngineManager sem = new ScriptEngineManager();
               requestContext = new HashMap<>();
               try {
                  // Evaluating request with script coming from operation dispatcher rules.
                  ScriptEngine se = sem.getEngineByExtension("groovy");
                  ScriptEngineBinder.bindEnvironment(se, body, requestContext,
                        new ServiceStateStore(serviceStateRepository, service.getId()), request);
                  String script = ScriptEngineBinder.ensureSoapUICompatibility(dispatcherRules);
                  dispatchCriteria = (String) se.eval(script);
               } catch (Exception e) {
                  log.error("Error during Script evaluation", e);
               }
               break;
            case DispatchStyles.URI_PARAMS:
               String fullURI = request.getRequestURL() + "?" + request.getQueryString();
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(dispatcherRules, fullURI);
               break;
            case DispatchStyles.URI_PARTS:
               // /tenantId?t1/userId=x
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(dispatcherRules, uriPattern,
                     resourcePath);
               break;
            case DispatchStyles.URI_ELEMENTS:
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(dispatcherRules, uriPattern,
                     resourcePath);
               fullURI = request.getRequestURL() + "?" + request.getQueryString();
               dispatchCriteria += DispatchCriteriaHelper.extractFromURIParams(dispatcherRules, fullURI);
               break;
            case DispatchStyles.JSON_BODY:
               try {
                  JsonEvaluationSpecification specification = JsonEvaluationSpecification
                        .buildFromJsonString(dispatcherRules);
                  dispatchCriteria = JsonExpressionEvaluator.evaluate(body, specification);
               } catch (JsonMappingException jme) {
                  log.error("Dispatching rules of operation cannot be interpreted as JsonEvaluationSpecification", jme);
               }
               break;
         }
      }

      return new DispatchContext(dispatchCriteria, requestContext);
   }

   /** Recopy headers defined with parameter constraints. */
   private void recopyHeadersFromParameterConstraints(Operation rOperation, HttpServletRequest request,
         HttpHeaders responseHeaders) {
      if (rOperation.getParameterConstraints() != null) {
         for (ParameterConstraint constraint : rOperation.getParameterConstraints()) {
            if (ParameterLocation.HEADER == constraint.getIn() && constraint.isRecopy()) {
               String value = request.getHeader(constraint.getName());
               if (value != null) {
                  responseHeaders.set(constraint.getName(), value);
               }
            }
         }
      }
   }

   /**
    * Filter responses using the Accept header for content-type, default to the first. Return null if no responses to
    * filter.
    */
   private Response getResponseByMediaType(List<Response> responses, HttpServletRequest request) {
      if (!responses.isEmpty()) {
         String accept = request.getHeader("Accept");
         return responses.stream().filter(r -> !StringUtils.isNotEmpty(accept) || accept.equals(r.getMediaType()))
               .findFirst().orElse(responses.get(0));
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

   private record MockInvocationContext(Service service, Operation operation, String resourcePath) {
   }
}
