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
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.repository.ServiceStateRepository;
import io.github.microcks.service.ProxyService;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;
import io.github.microcks.util.script.ScriptEngineBinder;
import io.github.microcks.service.ServiceStateStore;
import io.github.microcks.util.soap.SoapMessageValidator;
import io.github.microcks.util.soapui.SoapUIXPathBuilder;

import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.InputSource;

import jakarta.servlet.http.HttpServletRequest;

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

/**
 * A controller for mocking Soap responses.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/soap")
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
   private final ApplicationContext applicationContext;
   private final ProxyService proxyService;

   private ScriptEngine scriptEngine;

   @Value("${mocks.enable-invocation-stats}")
   private Boolean enableInvocationStats;

   @Value("${validation.resourceUrl}")
   private String resourceUrl;


   /**
    * Build a SoapController with required dependencies.
    * @param serviceRepository      The repository to access services definitions
    * @param serviceStateRepository The repository to access service state
    * @param responseRepository     The repository to access responses definitions
    * @param resourceRepository     The repository to access resources artifacts
    * @param applicationContext     The Spring application context
    * @param proxyService           The proxy to external URLs or services
    */
   public SoapController(ServiceRepository serviceRepository, ServiceStateRepository serviceStateRepository,
         ResponseRepository responseRepository, ResourceRepository resourceRepository,
         ApplicationContext applicationContext, ProxyService proxyService) {
      this.serviceRepository = serviceRepository;
      this.serviceStateRepository = serviceStateRepository;
      this.responseRepository = responseRepository;
      this.resourceRepository = resourceRepository;
      this.applicationContext = applicationContext;
      this.proxyService = proxyService;
      this.scriptEngine = new ScriptEngineManager().getEngineByExtension("groovy");
   }


   @PostMapping(value = "/{service}/{version}/**")
   public ResponseEntity<?> execute(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @RequestParam(value = "validate", required = false) Boolean validate,
         @RequestParam(value = "delay", required = false) Long delay, @RequestBody String body,
         @RequestHeader HttpHeaders headers, HttpServletRequest request, HttpMethod method) {
      log.info("Servicing mock response for service [{}, {}]", serviceName, version);
      log.debug("Request body: {}", body);

      long startTime = System.currentTimeMillis();

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
         return new ResponseEntity<>(
               String.format("The service %s with version %s does not exist!", serviceName, version),
               HttpStatus.NOT_FOUND);
      }
      Operation rOperation = null;

      // Enhancement : retrieve SOAPAction from request headers
      String action = extractSoapAction(request);
      log.debug("Extracted SOAP action from headers: {}", action);

      if (StringUtils.hasText(action)) {
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
         if (!StringUtils.hasText(action)) {
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

         if (validate != null && validate) {
            log.debug("Soap message validation is turned on, validating...");

            List<Resource> wsdlResources = resourceRepository.findByServiceIdAndType(service.getId(),
                  ResourceType.WSDL);
            if (wsdlResources.isEmpty()) {
               return new ResponseEntity<>(
                     String.format("The service %s with version %s does not have a wsdl!", serviceName, version),
                     HttpStatus.PRECONDITION_FAILED);
            }
            Resource wsdlResource = wsdlResources.get(0);
            List<String> errors = SoapMessageValidator.validateSoapMessage(wsdlResource.getContent(),
                  new QName(service.getXmlNS(), rOperation.getInputName()), body, resourceUrl);

            log.debug("SoapBody validation errors: {}", errors.size());

            // Return a 400 http code with errors.
            if (errors != null && !errors.isEmpty()) {
               return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
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

         Response response = null;
         DispatchContext dispatchContext = null;

         try {
            // Depending on dispatcher, evaluate request with rules.
            if (DispatchStyles.QUERY_MATCH.equals(dispatcher)) {
               dispatchContext = getDispatchCriteriaFromXPathEval(dispatcherRules, body);
            } else if (DispatchStyles.SCRIPT.equals(dispatcher)) {
               dispatchContext = getDispatchCriteriaFromScriptEval(service, dispatcherRules, body, request);
            } else if (DispatchStyles.RANDOM.equals(dispatcher)) {
               dispatchContext = new DispatchContext(DispatchStyles.RANDOM, null);
            } else if (DispatchStyles.PROXY.equals(dispatcher)) {
               dispatchContext = new DispatchContext(DispatchStyles.PROXY, null);
            } else {
               return new ResponseEntity<>(String.format("The dispatch %s is not supported!", dispatcher),
                     HttpStatus.NOT_FOUND);
            }
         } catch (ResponseStatusException e) {
            return new ResponseEntity<>(e.getMessage(), e.getStatusCode());
         }

         log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());
         List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
               IdBuilder.buildOperationId(service, rOperation), dispatchContext.dispatchCriteria());

         if (responses.isEmpty() && fallback != null) {
            // If we've found nothing and got a fallback, that's the moment!
            responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, rOperation),
                  fallback.getFallback());
         }

         Optional<URI> proxyUrl = MockControllerCommons.getProxyUrlIfProxyIsNeeded(dispatcher, dispatcherRules,
               MockControllerCommons.extractResourcePath(request, serviceAndVersion), proxyFallback, request,
               responses.isEmpty() ? null : responses.get(0));
         if (proxyUrl.isPresent()) {
            // If we've got a proxyUrl, that's the moment!
            return proxyService.callExternal(proxyUrl.get(), method, headers, body);
         }

         if (!responses.isEmpty()) {
            int idx = DispatchStyles.RANDOM.equals(dispatcher) ? RandomUtils.nextInt(0, responses.size()) : 0;
            response = responses.get(idx);
         } else {
            return new ResponseEntity<>(
                  String.format("The response %s does not exist!", dispatchContext.dispatchCriteria()),
                  HttpStatus.BAD_REQUEST);
         }

         // Set Content-Type to "text/xml".
         HttpHeaders responseHeaders = new HttpHeaders();

         // Check to see if we are processing a SOAP 1.2 request
         if (request.getContentType() == null || request.getContentType().startsWith("application/soap+xml")) {
            // we are; set Content-Type to "application/soap+xml"
            responseHeaders.setContentType(MediaType.valueOf("application/soap+xml;charset=UTF-8"));
         } else {
            // Set Content-Type to "text/xml".
            responseHeaders.setContentType(MediaType.valueOf("text/xml;charset=UTF-8"));
         }

         // Render response content before waiting and returning.
         // Response coming from SoapUI may contain specific template markers, we have to convert them first.
         response.setContent(convertSoapUITemplate(response.getContent()));
         String responseContent = MockControllerCommons.renderResponseContent(body, null, request,
               dispatchContext.requestContext(), response);

         // Setting delay to default one if not set.
         delay = MockControllerCommons.getDelay(headers, delay);
         if (delay == null && rOperation.getDefaultDelay() != null) {
            delay = rOperation.getDefaultDelay();
         }
         MockControllerCommons.waitForDelay(startTime, delay);

         // Publish an invocation event before returning if enabled.
         if (Boolean.TRUE.equals(enableInvocationStats)) {
            MockControllerCommons.publishMockInvocation(applicationContext, this, service, response, startTime);
         }

         if (response.isFault()) {
            return new ResponseEntity<>(responseContent, responseHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
         }
         return new ResponseEntity<>(responseContent, responseHeaders, HttpStatus.OK);
      }

      return new ResponseEntity<>(String.format("The operation %s does not exist!", action), HttpStatus.NOT_FOUND);
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
         log.error("Error during Xpath evaluation", e);
         throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
               "Error during Xpath evaluation: " + e.getMessage());
      }
   }

   /** Build a dispatch context after a Groovy script evaluation coming from rules. */
   private DispatchContext getDispatchCriteriaFromScriptEval(Service service, String dispatcherRules, String body,
         HttpServletRequest request) {
      Map<String, Object> requestContext = new HashMap<>();
      try {
         // Evaluating request with script coming from operation dispatcher rules.
         String script = ScriptEngineBinder.ensureSoapUICompatibility(dispatcherRules);
         ScriptContext scriptContext = ScriptEngineBinder.buildEvaluationContext(scriptEngine, body, requestContext,
               new ServiceStateStore(serviceStateRepository, service.getId()), request);

         return new DispatchContext((String) scriptEngine.eval(script, scriptContext), requestContext);
      } catch (Exception e) {
         log.error("Error during Script evaluation", e);
         throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
               "Error during Script evaluation: " + e.getMessage());
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
