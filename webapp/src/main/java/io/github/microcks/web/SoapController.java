/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.SoapMessageValidator;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.soapui.SoapUIScriptEngineBinder;
import io.github.microcks.util.soapui.SoapUIXPathBuilder;
import org.apache.xmlbeans.XmlError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.xml.sax.InputSource;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.http.HttpServletRequest;
import javax.xml.xpath.XPathExpression;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A controller for mocking Soap responses.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/soap")
public class SoapController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapController.class);

   private static Pattern operationCapturePattern = Pattern.compile("(.*):Body>(\\s*)<((\\w+):|)(?<operation>\\w+)(.*)(/)?>(.*)", Pattern.DOTALL);

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Autowired
   private ApplicationContext applicationContext;

   @Value("${validation.resourceUrl}")
   private final String resourceUrl = null;


   @RequestMapping(value = "/{service}/{version}/**", method = RequestMethod.POST)
   public ResponseEntity<?> execute(
         @PathVariable("service") String serviceName,
         @PathVariable("version") String version,
         @RequestParam(value="validate", required=false) Boolean validate,
         @RequestParam(value="delay", required=false) Long delay,
         @RequestBody String body,
         HttpServletRequest request
      ) {
      log.info("Servicing mock response for service [{}, {}]", serviceName, version);
      log.debug("Request body: " + body);

      long startTime = System.currentTimeMillis();

      // If serviceName was encoded with '+' instead of '%20', replace them.
      if (serviceName.contains("+")) {
         serviceName = serviceName.replace('+', ' ');
      }
      log.info("Service name: " + serviceName);
      // Retrieve service and correct operation.
      Service service = serviceRepository.findByNameAndVersion(serviceName, version);
      Operation rOperation = null;

      // Enhancement : retrieve SOAPAction from request headers
      String action = extractSoapAction(request);
      log.debug("Extracted SOAP action from headers: {}", action);

      if (action != null && action.length() > 0) {
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
         log.debug("Extracted operation name from payload: {}", operationName);

         if (operationName != null) {
            for (Operation operation : service.getOperations()) {
               if (operationName.equals(operation.getInputName()) || operationName.equals(operation.getName())) {
                  rOperation = operation;
                  log.info("Found valid operation {}", rOperation.getName());
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
            try {
               List<XmlError> errors = SoapMessageValidator.validateSoapMessage(
                     rOperation.getInputName(), service.getXmlNS(), body,
                     resourceUrl + service.getName() + "-" + version + ".wsdl", true);
               log.debug("SoapBody validation errors: " + errors.size());

               // Return a 400 http code with errors.
               if (errors != null && errors.size() > 0) {
                  return new ResponseEntity<Object>(errors, HttpStatus.BAD_REQUEST);
               }
            } catch (Exception e) {
               log.error("Error during Soap validation", e);
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

         Response response = null;
         String dispatchCriteria = null;

         // Depending on dispatcher, evaluate request with rules.
         if (DispatchStyles.QUERY_MATCH.equals(dispatcher)) {
            dispatchCriteria = getDispatchCriteriaFromXPathEval(dispatcherRules, body);

         } else if (DispatchStyles.SCRIPT.equals(dispatcher)) {
            dispatchCriteria = getDispatchCriteriaFromScriptEval(dispatcherRules, body, request);
         }

         log.debug("Dispatch criteria for finding response is {}", dispatchCriteria);
         List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
               IdBuilder.buildOperationId(service, rOperation), dispatchCriteria);

         if (responses.isEmpty() && fallback != null) {
            // If we've found nothing and got a fallback, that's the moment!
            responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, rOperation), fallback.getFallback());
         }

         if (!responses.isEmpty()) {
            response = responses.get(0);
         }

         // Set Content-Type to "text/xml".
         HttpHeaders responseHeaders = new HttpHeaders();

         if (request.getContentType().startsWith("application/soap+xml")) {
           responseHeaders.setContentType(MediaType.valueOf("application/soap+xml;charset=UTF-8"));
         } else {
           responseHeaders.setContentType(MediaType.valueOf("text/xml;charset=UTF-8"));
         }

         // Render response content before waiting and returning.
         String responseContent = MockControllerCommons.renderResponseContent(body, null, request, response);

         // Setting delay to default one if not set.
         if (delay == null && rOperation.getDefaultDelay() != null) {
            delay = rOperation.getDefaultDelay();
         }
         MockControllerCommons.waitForDelay(startTime, delay);

         // Publish an invocation event before returning.
         MockControllerCommons.publishMockInvocation(applicationContext, this, service, response, startTime);

         if (response.isFault()) {
            return new ResponseEntity<Object>(responseContent, responseHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
         }
         return new ResponseEntity<Object>(responseContent, responseHeaders, HttpStatus.OK);
      }

      log.debug("No valid operation found by Microcks...");
      return new ResponseEntity<Object>(HttpStatus.NOT_FOUND);
   }

   /**
    * Check if given SOAP payload has a correct structure for given operation name.
    * @param payload SOAP payload to check structure
    * @param operationName Name of operation to check structure against
    * @return True if payload is correct for operation, false otherwise.
    */
   protected boolean hasPayloadCorrectStructureForOperation(String payload, String operationName) {
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
   protected String extractOperationName(String payload) {
      Matcher matcher = operationCapturePattern.matcher(payload);
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
            action= action.substring(0, action.indexOf(";"));
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

   /** Build a dispatch criteria after a XPath evaluation coming from rules. */
   private String getDispatchCriteriaFromXPathEval(String dispatcherRules, String body) {
      try {
         // Evaluating request regarding XPath build with operation dispatcher rules.
         XPathExpression xpath = SoapUIXPathBuilder.buildXPathMatcherFromRules(dispatcherRules);
         return xpath.evaluate(new InputSource(new StringReader(body)));
      } catch (Exception e) {
         log.error("Error during Xpath evaluation", e);
      }
      return null;
   }

   /** Build a dipatch criteria after a Groovy script evaluation coming from rules. */
   private String getDispatchCriteriaFromScriptEval(String dispatcherRules, String body, HttpServletRequest request) {
      ScriptEngineManager sem = new ScriptEngineManager();
      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         SoapUIScriptEngineBinder.bindSoapUIEnvironment(se, body, request);
         return (String) se.eval(dispatcherRules);
      } catch (Exception e) {
         log.error("Error during Script evaluation", e);
      }
      return null;
   }
}
