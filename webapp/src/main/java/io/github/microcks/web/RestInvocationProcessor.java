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
import io.github.microcks.util.DataUriUtil;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.UTF8ContentTypeChecker;
import io.github.microcks.util.delay.DelaySpec;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.JsonExpressionEvaluator;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.script.JsScriptEngineBinder;
import io.github.microcks.util.script.ScriptEngineBinder;
import io.github.microcks.util.tracing.CommonAttributes;
import io.github.microcks.util.tracing.CommonEvents;
import io.github.microcks.service.OpenTelemetryResolverService;
import io.github.microcks.util.tracing.TraceUtil;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.roastedroot.quickjs4j.core.Engine;
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

import jakarta.servlet.http.HttpServletRequest;

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

import static io.github.microcks.util.tracing.CommonEvents.DISPATCH_CRITERIA_COMPUTED;

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

   private final ScriptEngine scriptEngine;
   private final OpenTelemetryResolverService opentelemetryResolverService;

   @Value("${mocks.enable-invocation-stats}")
   private Boolean enableInvocationStats;

   @Value("${mocks.enable-binary-response-decode:false}")
   private boolean enableBinaryResponseDecode;

   /**
    * Build a RestMockInvocationProcessor with required dependencies.
    * @param serviceStateRepository       The repository to access service state
    * @param responseRepository           The repository to access responses definitions
    * @param applicationContext           The Spring application context
    * @param proxyService                 The proxy to external URLs or services
    * @param opentelemetryResolverService The opentelemetry resolver
    */
   public RestInvocationProcessor(ServiceStateRepository serviceStateRepository, ResponseRepository responseRepository,
         ApplicationContext applicationContext, ProxyService proxyService,
         OpenTelemetryResolverService opentelemetryResolverService) {
      this.serviceStateRepository = serviceStateRepository;
      this.responseRepository = responseRepository;
      this.applicationContext = applicationContext;
      this.proxyService = proxyService;
      this.scriptEngine = new ScriptEngineManager().getEngineByExtension("groovy");
      this.opentelemetryResolverService = opentelemetryResolverService;
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
   @WithSpan(kind = SpanKind.INTERNAL, value = "processInvocation")
   public ResponseResult processInvocation(MockInvocationContext ic, long startTime, DelaySpec delay, String body,
         Map<String, List<String>> headers, HttpServletRequest request) {
      // Mark current span as an explain Span
      Span span = Span.current();
      TraceUtil.enableExplainTracing();

      // We must find dispatcher and its rules. Default to operation ones but
      // if we have a Fallback or Proxy-Fallback this is the one who is holding the first pass rules.
      FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(ic.operation());
      ProxyFallbackSpecification proxyFallback = MockControllerCommons.getProxyFallbackIfAny(ic.operation());
      String dispatcher = getDispatcher(ic, fallback, proxyFallback);
      String dispatcherRules = getDispatcherRules(ic, fallback, proxyFallback);

      // Add event about selected dispatcher.
      span.addEvent(CommonEvents.DISPATCHER_SELECTED.getEventName(),
            TraceUtil.explainSpanEventBuilder("Selected dispatcher and rules for this invocation")
                  .put(CommonAttributes.DISPATCHER, dispatcher != null ? dispatcher : "none")
                  .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules != null ? dispatcherRules : "none").build());

      DispatchContext dispatchContext = computeDispatchCriteria(ic.service(), dispatcher, dispatcherRules,
            getURIPattern(ic.operation().getName()), UriUtils.decode(ic.resourcePath(), StandardCharsets.UTF_8),
            request, body);
      log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());

      List<Response> responses;
      Response response = getResponse(ic, request, dispatchContext);

      span.addEvent(CommonEvents.RESPONSE_LOOKUP_COMPLETED.getEventName(),
            TraceUtil.explainSpanEventBuilder("Response lookup completed").put("response.found", response != null)
                  .put("response.name", response != null ? response.getName() : "null").build());

      if (response == null && fallback != null) {
         // If we've found nothing and got a fallback, that's the moment!
         span.addEvent(CommonEvents.FALLBACK_RESPONSE_USED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Using fallback response as no matching response was found")
                     .put("fallback.name", fallback.getFallback()).build());
         responses = responseRepository.findByOperationIdAndName(
               IdBuilder.buildOperationId(ic.service(), ic.operation()), fallback.getFallback());
         response = getResponseByMediaType(responses, request);
      }

      // Setting delay to default one if not set.
      if (delay == null && ic.operation().getDefaultDelay() != null) {
         delay = new DelaySpec(ic.operation().getDefaultDelay(), ic.operation().getDefaultDelayStrategy());
      }

      span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
            TraceUtil.explainSpanEventBuilder("Configured response delay")
                  .put(CommonAttributes.DELAY_VALUE, delay != null ? delay.baseValue() : 0)
                  .put(CommonAttributes.DELAY_STRATEGY, delay != null ? delay.strategyName() : "N/A").build());

      // Check if we need to proxy the request.
      Optional<URI> proxyUrl = MockControllerCommons.getProxyUrlIfProxyIsNeeded(dispatcher, dispatcherRules,
            ic.resourcePath(), proxyFallback, request, response);
      if (proxyUrl.isPresent()) {
         span.addEvent(CommonEvents.PROXY_REQUEST_INITIATED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Proxying request to external service")
                     .put("proxy.url", proxyUrl.get().toString()).put("proxy.method", ic.operation().getMethod())
                     .build());

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
            span.addEvent(CommonEvents.NO_DISPATCHER_FALLBACK_ATTEMPTED.getEventName(), TraceUtil
                  .explainSpanEventBuilder("No dispatcher configured, attempting to find any response for operation")
                  .build());
            response = getOneForOperation(ic, request, response);
         } else {
            // There is a dispatcher, but we found no response => return 400 as per #819 and #1132.
            span.addEvent(CommonEvents.NO_RESPONSE_FOUND.getEventName(),
                  TraceUtil.explainSpanEventBuilder("No matching response found for dispatch criteria")
                        .put(CommonAttributes.DISPATCH_CRITERIA, dispatchContext.dispatchCriteria())
                        .put(CommonAttributes.ERROR_STATUS, 400).build());
            span.setStatus(StatusCode.ERROR, "No matching response found for dispatch criteria");
            return new ResponseResult(HttpStatus.BAD_REQUEST, null,
                  String.format("The response %s does not exist!", dispatchContext.dispatchCriteria()).getBytes());
         }
      }

      if (response != null) {
         HttpStatus status = (response.getStatus() != null ? HttpStatus.valueOf(Integer.parseInt(response.getStatus()))
               : HttpStatus.OK);

         span.addEvent(CommonEvents.RESPONSE_SELECTED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Selected response to return")
                     .put(CommonAttributes.RESPONSE_NAME, response.getName())
                     .put(CommonAttributes.RESPONSE_STATUS, status.value())
                     .put("response.mediaType", response.getMediaType() != null ? response.getMediaType() : "none")
                     .build());

         // Deal with specific headers (content-type and redirect directive).
         HttpHeaders responseHeaders = getResponseHeaders(ic, body, request, dispatchContext, response);
         byte[] responseContent = getResponseContent(ic, startTime, delay, body, request, dispatchContext, response);

         // Return response content.
         return new ResponseResult(status, responseHeaders, responseContent);
      }

      span.addEvent(CommonEvents.NO_RESPONSE_AVAILABLE.getEventName(), TraceUtil
            .explainSpanEventBuilder("No response could be found or generated").put("error.status", 400).build());
      span.setStatus(StatusCode.ERROR, "No response could be found or generated");
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

      // Create an INTERNAL child span explicitly because Spring AOP does not apply to private/self-invoked methods.
      Tracer tracer = opentelemetryResolverService.getOpenTelemetry()
            .getTracer(RestInvocationProcessor.class.getName());
      Span childSpan = tracer.spanBuilder("computeDispatchCriteria").setSpanKind(SpanKind.INTERNAL).startSpan();

      try (Scope ignored = childSpan.makeCurrent()) {
         TraceUtil.enableExplainTracing();

         // Depending on dispatcher, evaluate request with rules.
         if (dispatcher != null) {
            switch (dispatcher) {
               case DispatchStyles.SEQUENCE:
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(dispatcherRules, uriPattern,
                        resourcePath);
                  break;
               case DispatchStyles.SCRIPT:
                  log.info("Use the \"GROOVY\" Dispatch Style instead.");
                  // fallthrough
               case DispatchStyles.GROOVY:
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
                     // Get current span and record failure
                     Span.current().recordException(e);
                     Span.current().addEvent(DISPATCH_CRITERIA_COMPUTED.getEventName(),
                           TraceUtil
                                 .explainSpanEventBuilder("Failed to compute dispatch criteria using GROOVY dispatcher")
                                 .put(CommonAttributes.DISPATCHER, "GROOVY")
                                 .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules)
                                 .put(CommonAttributes.DISPATCH_CRITERIA, "null").build());
                     Span.current().setStatus(StatusCode.ERROR, "Error during Script evaluation");
                     log.error("Error during Script evaluation", e);
                  }
                  break;
               case DispatchStyles.JS:
                  requestContext = new HashMap<>();
                  Map<String, String> jsUriParameters = DispatchCriteriaHelper.extractMapFromURIPattern(uriPattern,
                        resourcePath);
                  // Evaluating request with script coming from operation dispatcher rules.
                  String script = JsScriptEngineBinder.wrapIntoFunction(dispatcherRules);
                  Engine scriptContext = JsScriptEngineBinder.buildEvaluationContext(body, requestContext,
                        new ServiceStateStore(serviceStateRepository, service.getId()), request, jsUriParameters);
                  String result = JsScriptEngineBinder.invokeProcessFn(script, scriptContext);
                  if (result != null) {
                     dispatchCriteria = result;
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
                     log.error("Dispatching rules of operation cannot be interpreted as JsonEvaluationSpecification",
                           jme);
                     Span.current().recordException(jme);
                     Span.current().addEvent(DISPATCH_CRITERIA_COMPUTED.getEventName(), TraceUtil
                           .explainSpanEventBuilder("Failed to compute dispatch criteria using JSON_BODY dispatcher")
                           .put(CommonAttributes.DISPATCHER, "JSON_BODY")
                           .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules)
                           .put(CommonAttributes.DISPATCH_CRITERIA, "null").build());
                     Span.current().setStatus(StatusCode.ERROR, "Error during JSON_BODY evaluation");
                  }
                  break;
               case DispatchStyles.QUERY_HEADER:
                  // Extract headers from request and put them into a simple map to reuse extractFromParamMap().
                  dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules,
                        extractRequestHeaders(request));
                  break;
               default:
                  log.error("Unknown dispatcher type: {}", dispatcher);
                  Span.current().addEvent(DISPATCH_CRITERIA_COMPUTED.getEventName(),
                        TraceUtil.explainSpanEventBuilder("Unknown dispatcher type encountered")
                              .put(CommonAttributes.DISPATCHER, dispatcher)
                              .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules)
                              .put(CommonAttributes.DISPATCH_CRITERIA, "null").build());
                  break;
            }

            if (dispatchCriteria != null) {
               // Add an event about computed dispatch criteria.
               Span.current().addEvent(DISPATCH_CRITERIA_COMPUTED.getEventName(),
                     TraceUtil.explainSpanEventBuilder("Computed dispatch criteria using " + dispatcher + " dispatcher")
                           .put(CommonAttributes.DISPATCHER, dispatcher)
                           .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules)
                           .put(CommonAttributes.DISPATCH_CRITERIA, dispatchCriteria).build());
            }
         }
      } finally {
         childSpan.end();
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

   /**
    * Generates the response content to return, based on the media type (UTF-8 or Base64), applies an optional delay,
    * and publishes an invocation event if enabled.
    */
   private byte[] getResponseContent(MockInvocationContext ic, long startTime, DelaySpec delay, String body,
         HttpServletRequest request, DispatchContext dispatchContext, Response response) {

      byte[] responseContent;

      // Decide if we should treat content as UTF-8 (text) either because feature is disabled or media type is utf-8 encodable.
      boolean treatAsUtf8 = !enableBinaryResponseDecode
            || UTF8ContentTypeChecker.isUTF8Encodable(response.getMediaType());

      if (treatAsUtf8) {
         String content = MockControllerCommons.renderResponseContent(body, ic.resourcePath(), request,
               dispatchContext.requestContext(), response);
         responseContent = content != null ? content.getBytes(StandardCharsets.UTF_8) : null;

      } else {
         responseContent = tryDecodeExternalValueContent(response);
      }

      // Apply response delay and optionally publish the invocation event
      handlePostProcessing(startTime, delay, ic, response);

      return responseContent;
   }

   /* Attempts to decode the response content which is expected to be a data URI with Base64 encoded data. */
   private byte[] tryDecodeExternalValueContent(Response response) {
      if (response == null || response.getContent() == null) {
         return null;
      }
      try {
         return DataUriUtil.decodeDataUri(response.getContent());
      } catch (IllegalArgumentException e) {
         log.error("Error decoding response content as base64", e);
         log.debug("Returning response content as is");

         // Return raw content as UTF-8 if Base64 decoding fails
         if (response.getContent() != null) {
            return response.getContent().getBytes(StandardCharsets.UTF_8);
         } else {
            return null;
         }
      }
   }

   /**
    * Applies an artificial delay before returning the response, and publishes a mock invocation event if statistics
    * collection is enabled.
    */
   private void handlePostProcessing(long startTime, DelaySpec delay, MockInvocationContext ic, Response response) {
      MockControllerCommons.waitForDelay(startTime, delay);

      // Publish an invocation event before returning if enabled.
      if (Boolean.TRUE.equals(enableInvocationStats)) {
         MockControllerCommons.publishMockInvocation(applicationContext, this, ic.service(), response, startTime);
      }
   }
}
