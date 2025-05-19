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
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceStateRepository;
import io.github.microcks.service.ProxyService;
import io.github.microcks.service.ServiceStateStore;
import io.github.microcks.util.AbsoluteUrlMatcher;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.JsonExpressionEvaluator;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.script.ScriptEngineBinder;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
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
 * A processor for handling REST invocations. It is responsible for applying the dispatching logic and finding the most
 * appropriate response based on the request context.
 * @author laurent
 */
@Component
public class RestInvocationProcessor {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(RestInvocationProcessor.class);

   private final ServiceStateRepository serviceStateRepository;
   private final ResponseRepository responseRepository;
   private final ApplicationContext applicationContext;
   private final ProxyService proxyService;

   private ScriptEngine scriptEngine;

   @Value("${mocks.enable-invocation-stats}")
   private Boolean enableInvocationStats;

   /**
    * Build a RestMockInvocationProcessor with required dependencies.
    * @param serviceStateRepository The repository to access service state
    * @param responseRepository     The repository to access responses definitions
    * @param applicationContext     The Spring application context
    * @param proxyService           The proxy to external URLs or services
    */
   public RestInvocationProcessor(ServiceStateRepository serviceStateRepository, ResponseRepository responseRepository,
         ApplicationContext applicationContext, ProxyService proxyService) {
      this.serviceStateRepository = serviceStateRepository;
      this.responseRepository = responseRepository;
      this.applicationContext = applicationContext;
      this.proxyService = proxyService;
      this.scriptEngine = new ScriptEngineManager().getEngineByExtension("groovy");
   }

   /**
    * Process a REST invocation. This method is responsible for determining the appropriate response based on the
    * request context, applying any necessary dispatching logic, and handling proxying if required.
    * @param ic        The invocation context containing information about the service and operation being invoked
    * @param startTime The start time of the invocation
    * @param delay     The delay to apply before returning the response
    * @param body      The request body
    * @param headers   The HTTP headers of the request
    * @param request   The HTTP servlet request
    * @return A ResponseResult containing the status, headers, and body of the response
    */
   public ResponseResult processInvocation(MockInvocationContext ic, long startTime, Long delay, String body,
         Map<String, List<String>> headers, HttpServletRequest request) {

      // We must find dispatcher and its rules. Default to operation ones but
      // if we have a Fallback or Proxy-Fallback this is the one who is holding the first pass rules.
      FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(ic.operation());
      ProxyFallbackSpecification proxyFallback = MockControllerCommons.getProxyFallbackIfAny(ic.operation());
      String dispatcher = getDispatcher(ic, fallback, proxyFallback);
      String dispatcherRules = getDispatcherRules(ic, fallback, proxyFallback);

      //
      DispatchContext dispatchContext = computeDispatchCriteria(ic.service(), dispatcher, dispatcherRules,
            getURIPattern(ic.operation().getName()), UriUtils.decode(ic.resourcePath(), StandardCharsets.UTF_8),
            request, body);
      log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());

      List<Response> responses;
      Response response = getResponse(ic, request, dispatchContext);

      if (response == null && fallback != null) {
         // If we've found nothing and got a fallback, that's the moment!
         responses = responseRepository.findByOperationIdAndName(
               IdBuilder.buildOperationId(ic.service(), ic.operation()), fallback.getFallback());
         response = getResponseByMediaType(responses, request);
      }

      // Setting delay to default one if not set.
      if (delay == null && ic.operation().getDefaultDelay() != null) {
         delay = ic.operation().getDefaultDelay();
      }

      // Check if we need to proxy the request.
      Optional<URI> proxyUrl = MockControllerCommons.getProxyUrlIfProxyIsNeeded(dispatcher, dispatcherRules,
            ic.resourcePath(), proxyFallback, request, response);
      if (proxyUrl.isPresent()) {
         // Delay response here as the returning content will be returned directly.
         MockControllerCommons.waitForDelay(startTime, delay);

         // Translate generic headers into Spring ones.
         HttpHeaders httpHeaders = new HttpHeaders();
         httpHeaders.putAll(headers);

         // If we've got a proxyUrl, that's the moment!
         ResponseEntity<byte[]> proxyResponse = proxyService.callExternal(proxyUrl.get(),
               HttpMethod.valueOf(ic.operation().getMethod()), httpHeaders, body);
         return new ResponseResult(proxyResponse.getStatusCode(), proxyResponse.getHeaders(), proxyResponse.getBody());
      }

      if (response == null) {
         if (dispatcher == null) {
            response = getOneForOperation(ic, request, response);
         } else {
            // There is a dispatcher, but we found no response => return 400 as per #819 and #1132.
            return new ResponseResult(HttpStatus.BAD_REQUEST, null,
                  String.format("The response %s does not exist!", dispatchContext.dispatchCriteria()).getBytes());
         }
      }

      if (response != null) {
         HttpStatus status = (response.getStatus() != null ? HttpStatus.valueOf(Integer.parseInt(response.getStatus()))
               : HttpStatus.OK);

         // Deal with specific headers (content-type and redirect directive).
         HttpHeaders responseHeaders = getResponseHeaders(ic, body, request, dispatchContext, response);
         String responseContent = getResponseContent(ic, startTime, delay, body, request, dispatchContext, response);

         // Return response content.
         return new ResponseResult(status, responseHeaders,
               responseContent != null ? responseContent.getBytes(StandardCharsets.UTF_8) : null);
      }

      return new ResponseResult(HttpStatus.BAD_REQUEST, null, null);
   }

   /** Get the root dispatcher for the invocation context. */
   private String getDispatcher(MockInvocationContext ic, FallbackSpecification fallback,
         ProxyFallbackSpecification proxyFallback) {
      String dispatcher = ic.operation().getDispatcher();
      if (fallback != null) {
         dispatcher = fallback.getDispatcher();
      }
      if (proxyFallback != null) {
         dispatcher = proxyFallback.getDispatcher();
      }
      return dispatcher;
   }

   /** Get the root dispatcher rules for the invocation context. */
   private String getDispatcherRules(MockInvocationContext ic, FallbackSpecification fallback,
         ProxyFallbackSpecification proxyFallback) {
      String dispatcherRules = ic.operation().getDispatcherRules();
      if (fallback != null) {
         dispatcherRules = fallback.getDispatcherRules();
      }
      if (proxyFallback != null) {
         dispatcherRules = proxyFallback.getDispatcherRules();
      }
      return dispatcherRules;
   }

   /** Get one random response for operation. */
   private Response getOneForOperation(MockInvocationContext ic, HttpServletRequest request, Response response) {
      List<Response> responses;
      // In case no response found because dispatcher is null, just get one for the operation.
      // This will allow also OPTIONS operations (like pre-flight requests) with no dispatch criteria to work.
      log.debug("No responses found so far, tempting with just bare operationId...");
      responses = responseRepository.findByOperationId(IdBuilder.buildOperationId(ic.service(), ic.operation()));
      if (!responses.isEmpty()) {
         response = getResponseByMediaType(responses, request);
      }
      return response;
   }

   /** Filter responses using the Accept header for content-type, default to the first. Return null if no responses. */
   private Response getResponseByMediaType(List<Response> responses, HttpServletRequest request) {
      if (!responses.isEmpty()) {
         String accept = request.getHeader("Accept");
         return responses.stream().filter(r -> !StringUtils.isNotEmpty(accept) || accept.equals(r.getMediaType()))
               .findFirst().orElse(responses.getFirst());
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
               requestContext = new HashMap<>();
               Map<String, String> uriParameters = DispatchCriteriaHelper.extractMapFromURIPattern(uriPattern,
                     resourcePath);
               try {
                  // Evaluating request with script coming from operation dispatcher rules.
                  String script = ScriptEngineBinder.ensureSoapUICompatibility(dispatcherRules);
                  ScriptContext scriptContext = ScriptEngineBinder.buildEvaluationContext(scriptEngine, body,
                        requestContext, new ServiceStateStore(serviceStateRepository, service.getId()), request,
                        uriParameters);
                  dispatchCriteria = (String) scriptEngine.eval(script, scriptContext);
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
            case DispatchStyles.QUERY_HEADER:
               // Extract headers from request and put them into a simple map to reuse extractFromParamMap().
               dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules,
                     extractRequestHeaders(request));
               break;
            default:
               log.error("Unknown dispatcher type: {}", dispatcher);
               break;
         }
      }

      return new DispatchContext(dispatchCriteria, requestContext);
   }

   private Response getResponse(MockInvocationContext ic, HttpServletRequest request, DispatchContext dispatchContext) {
      Response response = null;

      // Filter depending on requested media type.
      // TODO: validate dispatchCriteria with dispatcherRules
      List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
            IdBuilder.buildOperationId(ic.service(), ic.operation()), dispatchContext.dispatchCriteria());
      response = getResponseByMediaType(responses, request);

      if (response == null) {
         // When using the SCRIPT or JSON_BODY dispatchers, return of evaluation may be the name of response.
         responses = responseRepository.findByOperationIdAndName(
               IdBuilder.buildOperationId(ic.service(), ic.operation()), dispatchContext.dispatchCriteria());
         response = getResponseByMediaType(responses, request);
      }
      return response;
   }

   /** Extract request headers from request. */
   private Map<String, String> extractRequestHeaders(HttpServletRequest request) {
      Map<String, String> headers = new HashMap<>();
      Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name, request.getHeader(name)));
      return headers;
   }

   private HttpHeaders getResponseHeaders(MockInvocationContext ic, String body, HttpServletRequest request,
         DispatchContext dispatchContext, Response response) {
      HttpHeaders responseHeaders = new HttpHeaders();
      if (response.getMediaType() != null) {
         responseHeaders.setContentType(MediaType.valueOf(response.getMediaType() + ";charset=UTF-8"));
      }

      // Deal with headers from parameter constraints if any?
      recopyHeadersFromParameterConstraints(ic.operation(), request, responseHeaders);

      // Adding other generic headers (caching directives and so on...)
      if (response.getHeaders() != null) {
         // First check if they should be rendered.
         EvaluableRequest evaluableRequest = MockControllerCommons.buildEvaluableRequest(body, ic.resourcePath(),
               request);
         Set<Header> renderedHeaders = MockControllerCommons.renderResponseHeaders(evaluableRequest,
               dispatchContext.requestContext(), response);

         handleHeaders(ic, request, responseHeaders, renderedHeaders);
      }
      return responseHeaders;
   }

   /** Recopy headers defined with parameter constraints. */
   private void recopyHeadersFromParameterConstraints(Operation rOperation, HttpServletRequest request,
         HttpHeaders responseHeaders) {
      if (rOperation.getParameterConstraints() != null) {
         for (ParameterConstraint constraint : rOperation.getParameterConstraints()) {
            if (ParameterLocation.header == constraint.getIn() && constraint.isRecopy()) {
               String value = request.getHeader(constraint.getName());
               if (value != null) {
                  responseHeaders.set(constraint.getName(), value);
               }
            }
         }
      }
   }

   private void handleHeaders(MockInvocationContext ic, HttpServletRequest request, HttpHeaders responseHeaders,
         Set<Header> renderedHeaders) {
      for (Header renderedHeader : renderedHeaders) {
         if ("Location".equals(renderedHeader.getName())) {
            String location = renderedHeader.getValues().iterator().next();
            if (!AbsoluteUrlMatcher.matches(location)) {
               // We should process location in order to make relative URI specified an absolute one from
               // the client perspective.
               location = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                     + request.getContextPath() + "/rest"
                     + MockControllerCommons.composeServiceAndVersion(ic.service().getName(), ic.service().getVersion())
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

   private String getResponseContent(MockInvocationContext ic, long startTime, Long delay, String body,
         HttpServletRequest request, DispatchContext dispatchContext, Response response) {
      // Render response content before waiting and returning.
      String responseContent = MockControllerCommons.renderResponseContent(body, ic.resourcePath(), request,
            dispatchContext.requestContext(), response);

      // Delay response.
      MockControllerCommons.waitForDelay(startTime, delay);

      // Publish an invocation event before returning if enabled.
      if (Boolean.TRUE.equals(enableInvocationStats)) {
         MockControllerCommons.publishMockInvocation(applicationContext, this, ic.service(), response, startTime);
      }
      return responseContent;
   }
}
