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
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceStateRepository;
import io.github.microcks.service.OpenTelemetryResolverService;
import io.github.microcks.service.ServiceStateStore;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.JsonExpressionEvaluator;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.grpc.GrpcMetadataUtil;
import io.github.microcks.util.script.JsScriptEngineBinder;
import io.github.microcks.util.script.ScriptEngineBinder;
import io.github.microcks.util.script.StringToStringsMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.roastedroot.quickjs4j.core.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.microcks.util.delay.DelaySpec;
import io.github.microcks.util.delay.DelayApplierOptions;
import io.github.microcks.util.tracing.CommonAttributes;
import io.github.microcks.util.tracing.CommonEvents;
import io.github.microcks.util.tracing.TraceUtil;

import static io.github.microcks.util.tracing.CommonEvents.DISPATCH_CRITERIA_COMPUTED;

/**
 * A processor for handling gRPC invocations. It is responsible for applying the dispatching logic and finding the most
 * appropriate response based on the request context.
 * @author laurent
 */
@Component
public class GrpcInvocationProcessor {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(GrpcInvocationProcessor.class);

   private final ServiceStateRepository serviceStateRepository;
   private final ResponseRepository responseRepository;
   private final ApplicationContext applicationContext;
   private final ObjectMapper mapper = new ObjectMapper();

   private final ScriptEngine scriptEngine;
   private final OpenTelemetryResolverService opentelemetryResolverService;

   @Value("${mocks.enable-invocation-stats}")
   private Boolean enableInvocationStats;

   /**
    * Build a GrpcInvocationProcessor with required dependencies.
    * @param serviceStateRepository       The repository to access service state
    * @param responseRepository           The repository to access responses definitions
    * @param applicationContext           The Spring application context
    * @param opentelemetryResolverService The opentelemetry resolver
    */
   public GrpcInvocationProcessor(ServiceStateRepository serviceStateRepository, ResponseRepository responseRepository,
         ApplicationContext applicationContext, OpenTelemetryResolverService opentelemetryResolverService) {
      this.serviceStateRepository = serviceStateRepository;
      this.responseRepository = responseRepository;
      this.applicationContext = applicationContext;
      this.scriptEngine = new ScriptEngineManager().getEngineByExtension("groovy");
      this.opentelemetryResolverService = opentelemetryResolverService;
   }

   /**
    * Process a gRPC invocation. This method is responsible for determining the appropriate response based on the
    * request context, applying any necessary dispatching logic.
    * @param ic        The invocation context containing information about the service and operation being invoked
    * @param startTime The start time of the invocation
    * @param jsonBody  The request body expressed in JSON
    * @return A GrcpResponseResult containing the status, body or exception message of the response
    */
   @WithSpan(kind = SpanKind.INTERNAL, value = "processInvocation")
   public GrpcResponseResult processInvocation(MockInvocationContext ic, long startTime, String jsonBody) {
      // Mark current span as an explain Span
      Span span = Span.current();
      TraceUtil.enableExplainTracing();

      // We must find dispatcher and its rules. Default to operation ones but
      // if we have a Fallback this is the one who is holding the first pass rules.
      String dispatcher = ic.operation().getDispatcher();
      String dispatcherRules = ic.operation().getDispatcherRules();
      FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(ic.operation());
      if (fallback != null) {
         dispatcher = fallback.getDispatcher();
         dispatcherRules = fallback.getDispatcherRules();
      }

      // Add event about selected dispatcher.
      span.addEvent(CommonEvents.DISPATCHER_SELECTED.getEventName(),
            TraceUtil.explainSpanEventBuilder("Selected dispatcher and rules for this invocation")
                  .put(CommonAttributes.DISPATCHER, dispatcher != null ? dispatcher : "none")
                  .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules != null ? dispatcherRules : "none").build());

      // Get metadata for current context.
      Metadata metadata = GrpcMetadataUtil.METADATA_CTX_KEY.get();
      DispatchContext dispatchContext = computeDispatchCriteria(ic.service(), dispatcher, dispatcherRules, jsonBody,
            metadata);
      log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());

      // Trying to retrieve the responses with context elements.
      List<Response> responses = findCandidateResponses(ic.service(), ic.operation(), dispatchContext, fallback);

      // No filter to apply, just check that we have a response.
      if (!responses.isEmpty()) {
         Response response = responses.getFirst();

         // Render response content before.
         String responseContent = MockControllerCommons.renderResponseContent(jsonBody,
               dispatchContext.requestContext(), response);

         // Setting delay to default one if not set.
         if (ic.operation().getDefaultDelay() != null) {
            DelaySpec delay = new DelaySpec(ic.operation().getDefaultDelay(), ic.operation().getDefaultDelayStrategy());
            span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Configured response delay")
                        .put(CommonAttributes.DELAY_VALUE, delay.baseValue())
                        .put(CommonAttributes.DELAY_STRATEGY, delay.strategyName()).build());

            MockControllerCommons.waitForDelay(startTime, delay);
         }

         // Publish an invocation event before returning if enabled.
         if (Boolean.TRUE.equals(enableInvocationStats)) {
            MockControllerCommons.publishMockInvocation(applicationContext, this, ic.service(), response, startTime);
         }

         // Return a GrpcResponseResult with the response content.
         if (response.getStatus() == null || response.getStatus().trim().equals("0")
               || statusInHttpRange(response.getStatus())) {
            return new GrpcResponseResult(Status.OK, responseContent, null);
         }

         // Or, return an error status.
         return new GrpcResponseResult(getSafeErrorStatus(response.getStatus()), null, "Mocked response status code");
      }

      // No response found.
      log.info("No appropriate response found for this input {}, returning an error", jsonBody);
      return new GrpcResponseResult(Status.NOT_FOUND, null, "No response found for the GRPC input request");
   }

   /** Compute a dispatch context with a dispatchCriteria string from type, rules and request elements. */
   private DispatchContext computeDispatchCriteria(Service service, String dispatcher, String dispatcherRules,
         String jsonBody, Metadata metadata) {
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
               case DispatchStyles.QUERY_ARGS:
                  try {
                     Map<String, String> paramsMap = mapper.readValue(jsonBody,
                           TypeFactory.defaultInstance().constructMapType(TreeMap.class, String.class, String.class));
                     dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules, paramsMap);
                  } catch (JsonProcessingException jpe) {
                     log.error("Incoming body cannot be parsed as JSON", jpe);
                  }
                  break;
               case DispatchStyles.JSON_BODY:
                  try {
                     JsonEvaluationSpecification specification = JsonEvaluationSpecification
                           .buildFromJsonString(dispatcherRules);
                     dispatchCriteria = JsonExpressionEvaluator.evaluate(jsonBody, specification);
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
               case DispatchStyles.SCRIPT:
                  log.info("Use the \"GROOVY\" Dispatch Style instead.");
                  // fallthrough
               case DispatchStyles.GROOVY:
                  requestContext = new HashMap<>();
                  try {
                     StringToStringsMap headers = GrpcMetadataUtil.convertToMap(metadata);
                     // Evaluating request with script coming from operation dispatcher rules.
                     ScriptContext scriptContext = ScriptEngineBinder.buildEvaluationContext(scriptEngine, jsonBody,
                           requestContext, new ServiceStateStore(serviceStateRepository, service.getId()), headers,
                           null);
                     dispatchCriteria = (String) scriptEngine.eval(dispatcherRules, scriptContext);
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
                  try {
                     StringToStringsMap headers = GrpcMetadataUtil.convertToMap(metadata);
                     // Evaluating request with script coming from operation dispatcher rules.
                     Engine scriptContext = JsScriptEngineBinder.buildEvaluationContext(jsonBody, requestContext,
                           new ServiceStateStore(serviceStateRepository, service.getId()), headers, null);
                     dispatchCriteria = JsScriptEngineBinder.invokeProcessFn(dispatcherRules, scriptContext);
                  } catch (Exception e) {
                     log.error("Error during Script evaluation", e);
                  }
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

   /** Find suitable responses regarding dispatch context and criteria. */
   private List<Response> findCandidateResponses(Service service, Operation grpcOperation,
         DispatchContext dispatchContext, FallbackSpecification fallback) {
      // Trying to retrieve the responses with dispatch criteria.
      List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
            IdBuilder.buildOperationId(service, grpcOperation), dispatchContext.dispatchCriteria());

      if (responses.isEmpty()) {
         // When using the SCRIPT or JSON_BODY dispatchers, return of evaluation may be the name of response.
         responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, grpcOperation),
               dispatchContext.dispatchCriteria());
      }

      Span.current().addEvent(CommonEvents.RESPONSE_LOOKUP_COMPLETED.getEventName(),
            TraceUtil.explainSpanEventBuilder("Response lookup completed").put("response.found", !responses.isEmpty())
                  .put("response.name", !responses.isEmpty() ? responses.getFirst().getName() : "null").build());

      if (responses.isEmpty() && fallback != null) {
         // If we've found nothing and got a fallback, that's the moment!
         Span.current().addEvent(CommonEvents.FALLBACK_RESPONSE_USED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Using fallback response as no matching response was found")
                     .put("fallback.name", fallback.getFallback()).build());
         responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, grpcOperation),
               fallback.getFallback());
      }

      if (responses.isEmpty()) {
         // In case no response found (because dispatcher is null for example), just get one for the operation.
         log.debug("No responses found so far, tempting with just bare operationId...");
         responses = responseRepository.findByOperationId(IdBuilder.buildOperationId(service, grpcOperation));
      }
      return responses;
   }

   /** Return true if status is in HTTP status range. */
   private boolean statusInHttpRange(String status) {
      try {
         int statusCode = Integer.parseInt(status);
         if (statusCode >= 200 && statusCode < 600) {
            log.warn("Response status code in incorrectly in HTTP code range: {}", status);
            return true;
         }
      } catch (NumberFormatException nfe) {
         log.warn("Invalid status code in response: {}", status);
      }
      return false;
   }

   /** Return a safe gRPC Status from a string. */
   private Status getSafeErrorStatus(String status) {
      try {
         return Status.fromCodeValue(Integer.parseInt(status));
      } catch (NumberFormatException nfe) {
         log.warn("Invalid status code in response: {}, using UNKNOWN", status);
         return Status.UNKNOWN;
      }
   }
}
