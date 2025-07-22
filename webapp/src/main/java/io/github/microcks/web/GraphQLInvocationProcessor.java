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
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceStateRepository;
import io.github.microcks.service.ProxyService;
import io.github.microcks.service.ServiceStateStore;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.JsonExpressionEvaluator;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;
import io.github.microcks.util.graphql.GraphQLHttpRequest;
import io.github.microcks.util.script.JsScriptEngineBinder;
import io.github.microcks.util.script.ScriptEngineBinder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.roastedroot.quickjs4j.core.Engine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A processor for handling GraphQL invocation requests. Is it responsible for applying dispatching logic and finding
 * the most appropriate response to return based on the request context.
 * @author laurent
 */
@Component
public class GraphQLInvocationProcessor {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(GraphQLInvocationProcessor.class);

   private final ServiceStateRepository serviceStateRepository;
   private final ResponseRepository responseRepository;
   private final ApplicationContext applicationContext;
   private final ProxyService proxyService;
   private final ObjectMapper mapper = new ObjectMapper();

   private ScriptEngine scriptEngine;

   @Value("${mocks.enable-invocation-stats}")
   private Boolean enableInvocationStats;

   /**
    * Build a GraphQLInvocationProcessor with required dependencies.
    * @param serviceStateRepository The repository to access service state
    * @param responseRepository     The repository to access responses definitions
    * @param applicationContext     The Spring application context
    * @param proxyService           The proxy to external URLs or services
    */
   public GraphQLInvocationProcessor(ServiceStateRepository serviceStateRepository,
         ResponseRepository responseRepository, ApplicationContext applicationContext, ProxyService proxyService) {
      this.serviceStateRepository = serviceStateRepository;
      this.responseRepository = responseRepository;
      this.applicationContext = applicationContext;
      this.proxyService = proxyService;
      this.scriptEngine = new ScriptEngineManager().getEngineByExtension("groovy");
   }

   /**
    * Process a GraphQL invocation. This method is responsible for determining the appropriate response based on the
    * request context, applying any necessary dispatching logic, and handling proxying if required.
    * @param ic             The invocation context containing information about the service and operation being invoked
    * @param startTime      The start time of the invocation
    * @param body           The request body
    * @param graphqlHttpReq The GraphQL HTTP request wrapper
    * @param headers        The HTTP headers of the request
    * @param request        The HTTP servlet request
    * @return A ResponseResult containing the status, headers, and body of the response
    */
   public ResponseResult processInvocation(MockInvocationContext ic, long startTime, Map<String, String> queryParams,
         String body, GraphQLHttpRequest graphqlHttpReq, Map<String, List<String>> headers,
         HttpServletRequest request) {
      // We must find dispatcher and its rules. Default to operation ones but
      // if we have a Fallback or Proxy-Fallback this is the one who is holding the first pass rules.
      FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(ic.operation());
      ProxyFallbackSpecification proxyFallback = MockControllerCommons.getProxyFallbackIfAny(ic.operation());
      String dispatcher = getDispatcher(ic, fallback, proxyFallback);
      String dispatcherRules = getDispatcherRules(ic, fallback, proxyFallback);

      //
      DispatchContext dispatchContext = computeDispatchCriteria(ic.service(), dispatcher, dispatcherRules, queryParams,
            graphqlHttpReq.getVariables(), request, body);
      log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());

      // First try: using computed dispatchCriteria on main dispatcher.
      Response response = null;
      List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
            IdBuilder.buildOperationId(ic.service(), ic.operation()), dispatchContext.dispatchCriteria());
      if (!responses.isEmpty()) {
         response = responses.getFirst();
      }

      if (response == null) {
         // When using the SCRIPT or JSON_BODY dispatcher, return of evaluation may be the name of response.
         log.debug("No responses with dispatch criteria, trying the name...");
         responses = responseRepository.findByOperationIdAndName(
               IdBuilder.buildOperationId(ic.service(), ic.operation()), dispatchContext.dispatchCriteria());
         if (!responses.isEmpty()) {
            response = responses.getFirst();
         }
      }

      if (response == null && fallback != null) {
         // If we've found nothing and got a fallback, that's the moment!
         log.debug("No responses till now so far, applying the fallback...");
         responses = responseRepository.findByOperationIdAndName(
               IdBuilder.buildOperationId(ic.service(), ic.operation()), fallback.getFallback());
         if (!responses.isEmpty()) {
            response = responses.getFirst();
         }
      }

      // Check if we need to proxy the request.
      Optional<URI> proxyUrl = MockControllerCommons.getProxyUrlIfProxyIsNeeded(dispatcher, dispatcherRules, "",
            proxyFallback, request, response);
      if (proxyUrl.isPresent()) {
         // Translate generic headers into Spring ones.
         HttpHeaders httpHeaders = new HttpHeaders();
         httpHeaders.putAll(headers);

         // If we've got a proxyUrl, that's the moment to tell about it!
         ResponseEntity<byte[]> proxyResponse = proxyService.callExternal(proxyUrl.get(),
               HttpMethod.valueOf(request.getMethod()), httpHeaders, body);
         return new ResponseResult(proxyResponse.getStatusCode(), proxyResponse.getHeaders(), proxyResponse.getBody());
      }

      if (response == null) {
         if (dispatcher == null) {
            // In case no response found (because dispatcher is null for example), just get one for the operation.
            // This will allow also OPTIONS operations (like pre-flight requests) with no dispatch criteria to work.
            log.debug("No responses found so far, tempting with just bare operationId...");
            responses = responseRepository.findByOperationId(IdBuilder.buildOperationId(ic.service(), ic.operation()));
            if (!responses.isEmpty()) {
               response = responses.getFirst();
            }
         }
      }

      if (response != null) {
         HttpStatus status = (response.getStatus() != null ? HttpStatus.valueOf(Integer.parseInt(response.getStatus()))
               : HttpStatus.OK);

         // Prepare headers for evaluation.
         Map<String, String> evaluableHeaders = new HashMap<>();
         if (response.getHeaders() != null) {
            for (Header header : response.getHeaders()) {
               evaluableHeaders.put(header.getName(), request.getHeader(header.getName()));
            }
         }

         // Render response content before waiting and returning.
         String responseContent = MockControllerCommons.renderResponseContent(body, null, evaluableHeaders,
               dispatchContext.requestContext(), response);


         // Evaluate headers and add them to responseHeaders.
         HttpHeaders responseHeaders = new HttpHeaders();
         if (response.getHeaders() != null) {
            response.getHeaders().stream()
                  .filter(header -> !HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(header.getName()))
                  .forEach(header -> responseHeaders.put(header.getName(), new ArrayList<>(header.getValues())));
         }

         // Publish an invocation event before returning if enabled.
         if (Boolean.TRUE.equals(enableInvocationStats)) {
            MockControllerCommons.publishMockInvocation(applicationContext, this, ic.service(), response, startTime);
         }

         // Return response content.
         return new ResponseResult(status, responseHeaders,
               responseContent != null ? responseContent.getBytes(StandardCharsets.UTF_8) : null);
      }

      // e found no response => return 400 as per #819 and #1132.
      return new ResponseResult(HttpStatus.BAD_REQUEST, null, null);
   }

   /** Compute a dispatch context with a dispatchCriteria string from type, rules and request elements. */
   private DispatchContext computeDispatchCriteria(Service service, String dispatcher, String dispatcherRules,
         Map<String, String> queryParams, JsonNode requestVariables, HttpServletRequest request, String body) {
      String dispatchCriteria = null;
      Map<String, Object> requestContext = null;

      // Depending on dispatcher, evaluate request with rules.
      if (dispatcher != null) {
         switch (dispatcher) {
            case DispatchStyles.QUERY_ARGS:
               dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules, queryParams);
               break;
            case DispatchStyles.JSON_BODY:
               try {
                  JsonEvaluationSpecification specification = JsonEvaluationSpecification
                        .buildFromJsonString(dispatcherRules);
                  dispatchCriteria = JsonExpressionEvaluator.evaluate(mapper.writeValueAsString(requestVariables),
                        specification);
               } catch (JsonMappingException jme) {
                  log.error("Dispatching rules of operation cannot be interpreted as JsonEvaluationSpecification", jme);
               } catch (JsonProcessingException jpe) {
                  log.error("Request variables cannot be serialized as Json for evaluation", jpe);
               }
               break;
            case DispatchStyles.SCRIPT:
               log.info("Use the \"GROOVY\" Dispatch Style instead.");
               // fallthrough
            case DispatchStyles.GROOVY:
               requestContext = new HashMap<>();
               try {
                  // Evaluating request with script coming from operation dispatcher rules.
                  ScriptContext scriptContext = ScriptEngineBinder.buildEvaluationContext(scriptEngine, body,
                        requestContext, new ServiceStateStore(serviceStateRepository, service.getId()), request);
                  dispatchCriteria = (String) scriptEngine.eval(dispatcherRules, scriptContext);
               } catch (Exception e) {
                  log.error("Error during Script evaluation", e);
               }
               break;
            case DispatchStyles.JS:
               Engine scriptContext = JsScriptEngineBinder.buildEvaluationContext(body, requestContext,
                     new ServiceStateStore(serviceStateRepository, service.getId()), request);
               String result = JsScriptEngineBinder.invokeProcessFn(dispatcherRules, scriptContext);
               if (result != null) {
                  dispatchCriteria = result;
               }
               break;
         }
      }
      return new DispatchContext(dispatchCriteria, requestContext);
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
}
