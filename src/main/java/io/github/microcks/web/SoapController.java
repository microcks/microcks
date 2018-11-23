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
import io.github.microcks.event.MockInvocationEvent;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.SoapMessageValidator;
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
import org.springframework.web.bind.annotation.*;
import org.xml.sax.InputSource;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.xml.xpath.XPathExpression;
import java.io.StringReader;
import java.util.Date;
import java.util.List;
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

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Autowired
   private ApplicationContext applicationContext;

   @Value("${validation.resourceUrl}")
   private final String resourceUrl = null;


   @RequestMapping(value = "/{service}/{version}", method = RequestMethod.POST)
   public ResponseEntity<?> execute(
         @PathVariable("service") String serviceName,
         @PathVariable("version") String version,
         @RequestParam(value="validate", required=false) Boolean validate,
         @RequestParam(value="delay", required=false) Long delay,
         @RequestBody String body
      ) {
      log.info("Servicing mock response for service [{}, {}]", serviceName, version);
      log.debug("Request body: " + body);

      long startTime = System.currentTimeMillis();

      // If serviceName was encoded with '+' instead of '%20', replace them.
      if (serviceName.contains("+")) {
         serviceName = serviceName.replace('+', ' ');
      }

      // Retrieve service and correct operation.
      Service service = serviceRepository.findByNameAndVersion(serviceName, version);
      Operation rOperation = null;
      for (Operation operation : service.getOperations()) {
         // Enhancement : try getting operation from soap:body directly!
         if (hasPayloadCorrectStructureForOperation(body, operation.getInputName())) {
            rOperation = operation;
            break;
         }
      }

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

         Response response = null;
         String dispatchCriteria = null;

         // Depending on dispatcher, evaluate request with rules.
         if (DispatchStyles.QUERY_MATCH.equals(rOperation.getDispatcher())) {
            dispatchCriteria = getDispatchCriteriaFromXPathEval(rOperation, body);

         } else if (DispatchStyles.SCRIPT.equals(rOperation.getDispatcher())) {
            dispatchCriteria = getDispatchCriteriaFromScriptEval(rOperation, body);
         }

         log.debug("Dispatch criteria for finding response is {}", dispatchCriteria);
         List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
               IdBuilder.buildOperationId(service, rOperation), dispatchCriteria);
         if (!responses.isEmpty()) {
            response = responses.get(0);
         }

         // Setting delay to default one if not set.
         if (delay == null && rOperation.getDefaultDelay() != null) {
            delay = rOperation.getDefaultDelay();
         }

         if (delay != null && delay > -1) {
            log.debug("Mock delay is turned on, waiting if necessary...");
            long duration = System.currentTimeMillis() - startTime;
            if (duration < delay) {
               Object semaphore = new Object();
               synchronized (semaphore) {
                  try {
                     semaphore.wait(delay - duration);
                  } catch (Exception e) {
                     log.debug("Delay semaphore was interrupted");
                  }
               }
            }
            log.debug("Delay now expired, releasing response !");
         }

         // Publish an invocation event before returning.
         MockInvocationEvent event = new MockInvocationEvent(this, service.getName(), version,
               response.getName(), new Date(startTime), startTime - System.currentTimeMillis());
         applicationContext.publishEvent(event);
         log.debug("Mock invocation event has been published");

         // Set Content-Type to "text/xml".
         HttpHeaders responseHeaders = new HttpHeaders();
         responseHeaders.setContentType(MediaType.valueOf("text/xml;charset=UTF-8"));
         if (response.isFault()) {
            return new ResponseEntity<Object>(response.getContent(), responseHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
         }
         return new ResponseEntity<Object>(response.getContent(), responseHeaders, HttpStatus.OK);
      }

      return new ResponseEntity<Object>(HttpStatus.NOT_FOUND);
   }

   /**
    * Check if given SOAP payload has a correct structure for given operation name.
    * @param payload SOAP payload to check structure
    * @param operationName Name of operation to check structure against
    * @return True if payload is correct for operation, false otherwise.
    */
   protected boolean hasPayloadCorrectStructureForOperation(String payload, String operationName) {
      String openingPattern = "(.*):Body>(\\s*)<(\\w+):" + operationName + "(.*)>(.*)";
      String closingPattern = "(.*)</(\\w+):" + operationName + ">(\\s*)</(.*):Body>(.*)";

      Pattern op = Pattern.compile(openingPattern, Pattern.DOTALL);
      Pattern cp = Pattern.compile(closingPattern, Pattern.DOTALL);

      return (op.matcher(payload).matches() && cp.matcher(payload).matches());
   }

   private String getDispatchCriteriaFromXPathEval(Operation operation, String body) {
      try {
         // Evaluating request regarding XPath build with operation dispatcher rules.
         XPathExpression xpath = SoapUIXPathBuilder.buildXPathMatcherFromRules(operation.getDispatcherRules());
         return xpath.evaluate(new InputSource(new StringReader(body)));
      } catch (Exception e) {
         log.error("Error during Xpath evaluation", e);
      }
      return null;
   }

   private String getDispatchCriteriaFromScriptEval(Operation operation, String body) {
      ScriptEngineManager sem = new ScriptEngineManager();
      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         SoapUIScriptEngineBinder.bindSoapUIEnvironment(se, body);
         return (String) se.eval(operation.getDispatcherRules());
      } catch (Exception e) {
         log.error("Error during Script evaluation", e);
      }
      return null;
   }
}
