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
package io.microcks.web;

import io.microcks.domain.Operation;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.ServiceStateRepository;
import io.microcks.event.MockInvocationEvent;
import io.microcks.service.ProxyService;
import io.microcks.util.DispatchStyles;
import io.microcks.util.IdBuilder;
import io.microcks.util.SafeLogger;
import io.microcks.util.delay.DelaySpec;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.http.HttpMethod;
import io.microcks.util.dispatcher.FallbackSpecification;
import io.microcks.util.dispatcher.ProxyFallbackSpecification;
import io.microcks.util.script.JsScriptEngineBinder;
import io.microcks.util.script.ScriptEngineBinder;
import io.microcks.service.ServiceStateStore;
import io.microcks.artifact.soap.SoapMessageValidator;
import io.microcks.artifact.soapui.SoapUIXPathBuilder;
import io.microcks.util.tracing.CommonAttributes;
import io.microcks.util.tracing.CommonEvents;
import io.microcks.util.tracing.TraceUtil;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.roastedroot.quickjs4j.core.Engine;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.http.HttpServerRequest;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.xml.sax.InputSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpression;
import java.io.StringReader;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.microcks.util.tracing.CommonEvents.DISPATCH_CRITERIA_COMPUTED;

/**
 * A controller for mocking Soap responses.
 * @author laurent
 */
@ApplicationScoped
@Path("/soap")
@RunOnVirtualThread
public class SoapController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(SoapController.class);

   /** Regular expression pattern for capturing Soap Operation name from body. */
   private static final Pattern OPERATION_CAPTURE_PATTERN = Pattern
         .compile("(.*):Body>(\\s*)<((\\w+):|)(?<operation>\\w+)(.*)(/)?>(.*)", Pattern.DOTALL);
   /** Regular expression replacement pattern for chnging SoapUI {@code ${}} in Microcks {@code {{}}}. */
   private static final Pattern SOAPUI_TEMPLATE_PARAMETER_REPLACE_PATTERN = Pattern
         .compile("\\$\\{\s*([a-zA-Z0-9-_]+)\s*\\}", Pattern.DOTALL);

   private final ServiceRepository serviceRepository;
   private final ServiceStateRepository serviceStateRepository;
   private final ResponseRepository responseRepository;
   private final ResourceRepository resourceRepository;
   private final Event<MockInvocationEvent> mockInvocationEvents;
   private final ProxyService proxyService;

   private final ScriptEngine scriptEngine;

   @ConfigProperty(name = "mocks.enable-invocation-stats", defaultValue = "true")
   Boolean enableInvocationStats;

   @ConfigProperty(name = "validation.resourceUrl", defaultValue = "http://localhost:8080/api/resources/")
   String resourceUrl;

   @ConfigProperty(name = "mocks.service-state-store.default-ttl", defaultValue = "10")
   int defaultTtl;

   /** The request parameters and context of a SOAP mock invocation. */
   public static class SoapParams {
      @PathParam("service")
      String service;
      @PathParam("version")
      String version;
      @QueryParam("validate")
      Boolean validate;
      @QueryParam("delay")
      Long delay;
      @QueryParam("delayStrategy")
      String delayStrategy;
      @Context
      UriInfo uriInfo;
      @Context
      jakarta.ws.rs.core.HttpHeaders headers;
      @Context
      HttpServerRequest httpRequest;
   }

   /** Internal failure carrying the HTTP status to return. */
   private static class DispatchException extends RuntimeException {
      private final int status;

      DispatchException(int status, String message) {
         super(message);
         this.status = status;
      }
   }

   /**
    * Build a SoapController with required dependencies.
    * @param serviceRepository      The repository to access services definitions
    * @param serviceStateRepository The repository to access service state
    * @param responseRepository     The repository to access responses definitions
    * @param resourceRepository     The repository to access resources artifacts
    * @param mockInvocationEvents   The publisher of mock invocation events
    * @param proxyService           The proxy to external URLs or services
    */
   @Inject
   public SoapController(ServiceRepository serviceRepository, ServiceStateRepository serviceStateRepository,
         ResponseRepository responseRepository, ResourceRepository resourceRepository,
         Event<MockInvocationEvent> mockInvocationEvents, ProxyService proxyService) {
      this.serviceRepository = serviceRepository;
      this.serviceStateRepository = serviceStateRepository;
      this.responseRepository = responseRepository;
      this.resourceRepository = resourceRepository;
      this.mockInvocationEvents = mockInvocationEvents;
      this.proxyService = proxyService;
      this.scriptEngine = new ScriptEngineManager().getEngineByExtension("groovy");
   }

   @POST
   @Path("/{service}/{version}{path:(/.*)?}")
   public jakarta.ws.rs.core.Response execute(@BeanParam SoapParams params, String body) {
      String serviceName = params.service;
      String version = params.version;
      Boolean validate = params.validate;
      Long requestedDelay = params.delay;
      String requestedDelayStrategy = params.delayStrategy;
      HttpMethod method = HttpMethod.POST;
      HttpServletRequest request = ContainerHttpServletRequest.from("POST", params.uriInfo, params.headers,
            params.httpRequest);
      HttpHeaders headers = new HttpHeaders();
      headers.putAll(params.headers.getRequestHeaders());
      if (body != null && body.isEmpty()) {
         body = null;
      }
      log.info("Servicing mock response for service [{}, {}]", serviceName, version);
      log.debug("Request body: {}", body);

      long startTime = System.currentTimeMillis();

      // Add an event for the invocation reception with a human-friendly message.
      Span span = Span.current();
      TraceUtil.enableExplainTracing();
      span.addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(), TraceUtil
            .explainSpanEventBuilder(String.format("Received SOAP invocation for service %s/%s", serviceName, version))
            .put(CommonAttributes.BODY_SIZE, body != null ? body.length() : 0)
            .put(CommonAttributes.BODY_CONTENT,
                  body != null ? (body.length() > 1000 ? body.substring(0, 1000) + "..." : body) : "empty")
            .put(CommonAttributes.URI_FULL, request.getRequestURL().toString())
            .put(CommonAttributes.CLIENT_ADDRESS, request.getRemoteAddr()).build());

      // Setup serviceAndVersion for proxy dispatchers
      String serviceAndVersion = MockControllerCommons.composeServiceAndVersion(serviceName, version);

      // If serviceName was encoded with '+' instead of '%20', replace them.
      if (serviceName.contains("+")) {
         serviceName = serviceName.replace('+', ' ');
      }
      log.debug("Service name: {}", serviceName);
      // Retrieve service and correct operation.
      Service service = serviceRepository.findByNameAndVersion(serviceName, version);
      if (service == null) {
         String msg = String.format("The service %s with version %s does not exist!", serviceName, version);
         span.addEvent(CommonEvents.SERVICE_NOT_FOUND.getEventName(), TraceUtil.explainSpanEventBuilder(msg).build());
         span.setStatus(StatusCode.ERROR, msg);
         return text(404, msg);
      }
      span.setAttribute(CommonAttributes.SERVICE_NAME, service.getName());
      span.setAttribute(CommonAttributes.SERVICE_VERSION, service.getVersion());
      Operation rOperation = null;

      // Enhancement : retrieve SOAPAction from request headers
      String action = extractSoapAction(request);
      log.debug("Extracted SOAP action from headers: {}", action);

      if (StringUtils.isNotBlank(action)) {
         for (Operation operation : service.getOperations()) {
            if (action.equals(operation.getAction())) {
               rOperation = operation;
               log.info("Found valid operation {}", rOperation.getName());
               break;
            }
         }
      }

      // Enhancement : if not found, try getting operation from soap:body directly!
      if (rOperation == null) {
         String operationName = extractOperationName(body);
         if (StringUtils.isBlank(action)) {
            // if the action is not in the header, we override it with the action from the body
            action = operationName;
         }
         log.debug("Extracted operation name from payload: {}", operationName);

         if (operationName != null) {
            for (Operation operation : service.getOperations()) {
               if (operationName.equals(operation.getInputName()) || operationName.equals(operation.getName())) {
                  rOperation = operation;
                  log.debug("Found valid operation {}", rOperation.getName());
                  break;
               }
            }
         }
      }

      // Now processing the request and send a response.
      if (rOperation != null) {
         log.debug("Found a valid operation with rules: {}", rOperation.getDispatcherRules());

         // Set operation-level span attributes.
         span.setAttribute(CommonAttributes.OPERATION_NAME, rOperation.getName());
         span.setAttribute(CommonAttributes.OPERATION_ACTION, action != null ? action : "none");

         if (validate != null && validate) {
            log.debug("Soap message validation is turned on, validating...");

            List<Resource> wsdlResources = resourceRepository.findByServiceIdAndType(service.getId(),
                  ResourceType.WSDL);
            if (wsdlResources.isEmpty()) {
               return text(412,
                     String.format("The service %s with version %s does not have a wsdl!", serviceName, version));
            }
            Resource wsdlResource = wsdlResources.get(0);
            List<String> errors = SoapMessageValidator.validateSoapMessage(wsdlResource.getContent(),
                  new QName(service.getXmlNS(), rOperation.getInputName()), body, resourceUrl);

            log.debug("SoapBody validation errors: {}", errors.size());

            // Return a 400 http code with errors.
            if (errors != null && !errors.isEmpty()) {
               return jakarta.ws.rs.core.Response.status(400).type(MediaType.APPLICATION_JSON).entity(errors).build();
            }
         }

         // We must find dispatcher and its rules. Default to operation ones but
         // if we have a Fallback this is the one who is holding the first pass rules.
         String dispatcher = rOperation.getDispatcher();
         String dispatcherRules = rOperation.getDispatcherRules();
         FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(rOperation);
         if (fallback != null) {
            dispatcher = fallback.getDispatcher();
            dispatcherRules = fallback.getDispatcherRules();
         }
         ProxyFallbackSpecification proxyFallback = MockControllerCommons.getProxyFallbackIfAny(rOperation);
         if (proxyFallback != null) {
            dispatcher = proxyFallback.getDispatcher();
            dispatcherRules = proxyFallback.getDispatcherRules();
         }

         span.addEvent(CommonEvents.DISPATCHER_SELECTED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Selected dispatcher and rules for this invocation")
                     .put(CommonAttributes.DISPATCHER, dispatcher != null ? dispatcher : "none")
                     .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules != null ? dispatcherRules : "none")
                     .build());

         Response response = null;
         DispatchContext dispatchContext = null;

         try {
            // Depending on dispatcher, evaluate request with rules.
            if (DispatchStyles.QUERY_MATCH.equals(dispatcher)) {
               dispatchContext = getDispatchCriteriaFromXPathEval(dispatcherRules, body);
            } else if (DispatchStyles.SCRIPT.equals(dispatcher) || DispatchStyles.GROOVY.equals(dispatcher)) {
               dispatchContext = getDispatchCriteriaFromGroovyEval(service, dispatcherRules, body, request);
            } else if (DispatchStyles.JS.equals(dispatcher)) {
               dispatchContext = getDispatchCriteriaFromJsEval(service, dispatcherRules, body, request);
            } else if (DispatchStyles.RANDOM.equals(dispatcher)) {
               dispatchContext = new DispatchContext(DispatchStyles.RANDOM, null);
            } else if (DispatchStyles.PROXY.equals(dispatcher)) {
               dispatchContext = new DispatchContext(DispatchStyles.PROXY, null);
            } else {
               return text(404, String.format("The dispatch %s is not supported!", dispatcher));
            }
         } catch (DispatchException e) {
            return text(e.status, e.getMessage());
         }

         if (dispatchContext.dispatchCriteria() != null) {
            // Add an event about computed dispatch criteria.
            Span.current().addEvent(DISPATCH_CRITERIA_COMPUTED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Computed dispatch criteria using " + dispatcher + " dispatcher")
                        .put(CommonAttributes.DISPATCHER, dispatcher)
                        .put(CommonAttributes.DISPATCHER_RULES, dispatcherRules)
                        .put(CommonAttributes.DISPATCH_CRITERIA, dispatchContext.dispatchCriteria()).build());
         }

         log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());
         List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
               IdBuilder.buildOperationId(service, rOperation), dispatchContext.dispatchCriteria());

         span.addEvent(CommonEvents.RESPONSE_LOOKUP_COMPLETED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Response lookup completed")
                     .put(CommonAttributes.RESPONSE_FOUND, !responses.isEmpty())
                     .put(CommonAttributes.RESPONSE_NAME, !responses.isEmpty() ? responses.get(0).getName() : "null")
                     .build());

         if (responses.isEmpty() && fallback != null) {
            // If we've found nothing and got a fallback, that's the moment!
            span.addEvent(CommonEvents.FALLBACK_RESPONSE_USED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Using fallback response as no matching response was found")
                        .put("fallback.name", fallback.getFallback()).build());
            responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, rOperation),
                  fallback.getFallback());
         }

         Optional<URI> proxyUrl = MockControllerCommons.getProxyUrlIfProxyIsNeeded(dispatcher, dispatcherRules,
               MockControllerCommons.extractResourcePath(request, serviceAndVersion), proxyFallback, request,
               responses.isEmpty() ? null : responses.get(0));
         if (proxyUrl.isPresent()) {
            // If we've got a proxyUrl, that's the moment!
            span.addEvent(CommonEvents.PROXY_REQUEST_INITIATED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Proxying request to external service")
                        .put("proxy.url", proxyUrl.get().toString())
                        .put("proxy.method", method != null ? method.toString() : "POST").build());
            return RestController
                  .toResponse(proxyService.callExternal(proxyUrl.get(), method, headers.asMultiValueMap(), body));
         }

         if (!responses.isEmpty()) {
            int idx = DispatchStyles.RANDOM.equals(dispatcher) ? RandomUtils.nextInt(0, responses.size()) : 0;
            response = responses.get(idx);
         } else {
            span.addEvent(CommonEvents.NO_RESPONSE_FOUND.getEventName(),
                  TraceUtil.explainSpanEventBuilder("No matching response found for dispatch criteria")
                        .put(CommonAttributes.DISPATCH_CRITERIA, dispatchContext.dispatchCriteria())
                        .put(CommonAttributes.ERROR_STATUS, 400).build());
            span.setStatus(StatusCode.ERROR, "No matching response found for dispatch criteria");
            return text(400, String.format("The response %s does not exist!", dispatchContext.dispatchCriteria()));
         }

         span.addEvent(CommonEvents.RESPONSE_SELECTED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Selected response to return")
                     .put(CommonAttributes.RESPONSE_NAME, response.getName())
                     .put("response.mediaType", response.getMediaType() != null ? response.getMediaType() : "none")
                     .build());

         // Set Content-Type to "text/xml".
         String responseContentType;

         // Check to see if we are processing a SOAP 1.2 request
         if (request.getContentType() == null || request.getContentType().startsWith("application/soap+xml")) {
            // we are; set Content-Type to "application/soap+xml"
            responseContentType = "application/soap+xml;charset=UTF-8";
         } else {
            // Set Content-Type to "text/xml".
            responseContentType = "text/xml;charset=UTF-8";
         }

         // Render response content before waiting and returning.
         // Response coming from SoapUI may contain specific template markers, we have to convert them first.
         response.setContent(convertSoapUITemplate(response.getContent()));
         String responseContent = MockControllerCommons.renderResponseContent(body, null, request,
               dispatchContext.requestContext(), response);

         // Setting delay to default one if not set.
         DelaySpec delay = MockControllerCommons.getDelay(headers, requestedDelay, requestedDelayStrategy);
         if (delay == null && rOperation.getDefaultDelay() != null) {
            delay = new DelaySpec(rOperation.getDefaultDelay(), rOperation.getDefaultDelayStrategy());
         }

         span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Configured response delay")
                     .put(CommonAttributes.DELAY_VALUE, delay != null ? delay.baseValue() : 0)
                     .put(CommonAttributes.DELAY_STRATEGY, delay != null ? delay.strategyName() : "N/A").build());

         MockControllerCommons.waitForDelay(startTime, delay);

         // Publish an invocation event before returning if enabled.
         if (Boolean.TRUE.equals(enableInvocationStats)) {
            MockControllerCommons.publishMockInvocation(mockInvocationEvents, service, response, startTime);
         }

         return jakarta.ws.rs.core.Response.status(response.isFault() ? 500 : 200)
               .header("Content-Type", responseContentType).entity(responseContent).build();
      }

      String msg = String.format("The operation %s does not exist!", action);
      span.addEvent(CommonEvents.OPERATION_NOT_FOUND.getEventName(), TraceUtil.explainSpanEventBuilder(msg).build());
      span.setStatus(StatusCode.ERROR, msg);
      return text(404, msg);
   }

   /** Build a plain text response. */
   private static jakarta.ws.rs.core.Response text(int status, String message) {
      return jakarta.ws.rs.core.Response.status(status).type(MediaType.TEXT_PLAIN).entity(message).build();
   }

   /**
    * Check if given SOAP payload has a correct structure for given operation name.
    * @param payload       SOAP payload to check structure
    * @param operationName Name of operation to check structure against
    * @return True if payload is correct for operation, false otherwise.
    */
   protected static boolean hasPayloadCorrectStructureForOperation(String payload, String operationName) {
      String openingPattern = "(.*):Body>(\\s*)<((\\w+):|)" + operationName + "(.*)>(.*)";
      String closingPattern = "(.*)</((\\w+):|)" + operationName + ">(\\s*)</(.*):Body>(.*)";
      String shortPattern = "(.*):Body>(\\s*)<((\\w+):|)" + operationName + "(.*)/>(\\s*)</(.*):Body>(.*)";

      Pattern op = Pattern.compile(openingPattern, Pattern.DOTALL);
      Pattern cp = Pattern.compile(closingPattern, Pattern.DOTALL);
      Pattern sp = Pattern.compile(shortPattern, Pattern.DOTALL);
      return (op.matcher(payload).matches() && cp.matcher(payload).matches()) || sp.matcher(payload).matches();
   }

   /**
    * Extract operation name from payload. Indeed we extract the wrapping element name inside SOAP body.
    * @param payload SOAP payload to extract from
    * @return The wrapping Xml element name with body if matches SOAP. Null otherwise.
    */
   protected static String extractOperationName(String payload) {
      if (payload == null) {
         return null;
      }
      Matcher matcher = OPERATION_CAPTURE_PATTERN.matcher(payload);
      if (matcher.find()) {
         return matcher.group("operation");
      }
      return null;
   }

   /**
    * Extraction Soap Action from request headers if specified.
    * @param request The incoming HttpServletRequest to extract from
    * @return The found Soap action if any. Can be null.
    */
   protected String extractSoapAction(HttpServletRequest request) {
      String action = null;
      // If Soap 1.2, SOAPAction is in Content-Type header.
      String contentType = request.getContentType();
      if (contentType != null && contentType.startsWith("application/soap+xml") && contentType.contains("action=")) {
         action = contentType.substring(contentType.indexOf("action=") + 7);
         // Remove any other optional param in content-type if any.
         if (action.contains(";")) {
            action = action.substring(0, action.indexOf(";"));
         }
      } else {
         // Else, SOAPAction is in dedicated header.
         action = request.getHeader("SOAPAction");
      }
      // Sanitize action value if any.
      if (action != null) {
         // Remove starting double-quote if any.
         if (action.startsWith("\"")) {
            action = action.substring(1);
         }
         // Remove ending double-quote if any.
         if (action.endsWith("\"")) {
            action = action.substring(0, action.length() - 1);
         }
      }
      return action;
   }

   /** Build a dispatch context after a XPath evaluation coming from rules. */
   private DispatchContext getDispatchCriteriaFromXPathEval(String dispatcherRules, String body) {
      try {
         // Evaluating request regarding XPath build with operation dispatcher rules.
         XPathExpression xpath = SoapUIXPathBuilder.buildXPathMatcherFromRules(dispatcherRules);
         return new DispatchContext(xpath.evaluate(new InputSource(new StringReader(body))), null);
      } catch (Exception e) {
         // Get current span and record failure
         Span.current().recordException(e);
         Span.current().addEvent(DISPATCH_CRITERIA_COMPUTED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Failed to compute dispatch criteria using XPATH dispatcher")
                     .put(CommonAttributes.DISPATCHER, "XPATH").put(CommonAttributes.DISPATCHER_RULES, dispatcherRules)
                     .put(CommonAttributes.DISPATCH_CRITERIA, "null").build());
         Span.current().setStatus(StatusCode.ERROR, "Error during Xpath evaluation");

         log.error("Error during Xpath evaluation", e);
         throw new DispatchException(500, "Error during Xpath evaluation: " + e.getMessage());
      }
   }

   /** Build a dispatch context after a Groovy script evaluation coming from rules. */
   private DispatchContext getDispatchCriteriaFromGroovyEval(Service service, String dispatcherRules, String body,
         HttpServletRequest request) {
      Map<String, Object> requestContext = new HashMap<>();
      try {
         // Evaluating request with script coming from operation dispatcher rules.
         String script = ScriptEngineBinder.ensureSoapUICompatibility(dispatcherRules);
         ScriptContext scriptContext = ScriptEngineBinder.buildEvaluationContext(scriptEngine, body, requestContext,
               new ServiceStateStore(serviceStateRepository, service.getId(), defaultTtl), request);

         return new DispatchContext((String) scriptEngine.eval(script, scriptContext), requestContext);
      } catch (Exception e) {
         // Get current span and record failure
         Span.current().recordException(e);
         Span.current().addEvent(DISPATCH_CRITERIA_COMPUTED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Failed to compute dispatch criteria using GROOVY dispatcher")
                     .put(CommonAttributes.DISPATCHER, "GROOVY").put(CommonAttributes.DISPATCHER_RULES, dispatcherRules)
                     .put(CommonAttributes.DISPATCH_CRITERIA, "null").build());
         Span.current().setStatus(StatusCode.ERROR, "Error during Script evaluation");

         log.error("Error during Script evaluation", e);
         throw new DispatchException(500, "Error during Script evaluation: " + e.getMessage());
      }
   }

   /* Build a dispatch context after a JS script evaluation coming from rules. */
   private DispatchContext getDispatchCriteriaFromJsEval(Service service, String dispatcherRules, String body,
         HttpServletRequest request) {
      Map<String, Object> requestContext = new HashMap<>();
      try {
         Engine scriptContext = JsScriptEngineBinder.buildEvaluationContext(body, requestContext,
               new ServiceStateStore(serviceStateRepository, service.getId(), defaultTtl), request);

         return new DispatchContext(JsScriptEngineBinder.invokeProcessFn(dispatcherRules, scriptContext),
               requestContext);
      } catch (Exception e) {
         log.error("Error during JS evaluation", e);
         throw new DispatchException(500, "Error during JS evaluation: " + e.getMessage());
      }
   }

   /**
    * Convert a SoapUI template like {@code <something>${myParam}</something>} into a Microcks one that could be later
    * rendered through the template engine. ie: {@code <something>{{ myParam }}</something>}. Supports multi-lines and
    * multi-parameters replacement.
    * @param responseTemplate The SoapUI template to convert
    * @return The converted template or the original template if not recognized as a SoapUI one.
    */
   protected static String convertSoapUITemplate(String responseTemplate) {
      if (responseTemplate.contains("${")) {
         return SOAPUI_TEMPLATE_PARAMETER_REPLACE_PATTERN.matcher(responseTemplate).replaceAll("{{ $1 }}");
      }
      return responseTemplate;
   }
}
