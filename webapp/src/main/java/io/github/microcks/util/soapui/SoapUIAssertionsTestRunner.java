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
package io.github.microcks.util.soapui;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.util.MalformedXmlException;
import io.github.microcks.util.soapui.assertions.AssertionFactory;
import io.github.microcks.util.soapui.assertions.AssertionStatus;
import io.github.microcks.util.soapui.assertions.ExchangeContext;
import io.github.microcks.util.soapui.assertions.RequestResponseExchange;
import io.github.microcks.util.soapui.assertions.SoapUIAssertion;
import io.github.microcks.util.test.HttpTestRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.microcks.util.soapui.SoapUIProjectParserUtils.getConfigDirectChildren;
import static io.github.microcks.util.soapui.SoapUIProjectParserUtils.getConfigUniqueDirectChild;
import static io.github.microcks.util.soapui.SoapUIProjectParserUtils.hasConfigDirectChild;

/**
 * This is a utility class for running Service tests using assertions defined under a corresponding SoapUI project.
 * @author laurent
 */
public class SoapUIAssertionsTestRunner extends HttpTestRunner {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(SoapUIAssertionsTestRunner.class);

   /** The URL of resources used for validation. */
   private String resourceUrl = null;

   private final ResourceRepository resourceRepository;
   private final Map<ResourceType, Resource> cachedResources = new HashMap<>();

   private Element projectElement;

   private List<String> lastValidationErrors = null;

   private long startTimestamp;


   public SoapUIAssertionsTestRunner(ResourceRepository resourceRepository) {
      this.resourceRepository = resourceRepository;
   }

   /**
    * The URL of resources used for validation.
    * @return The URL of resources used for validation
    */
   public String getResourceUrl() {
      return resourceUrl;
   }

   /**
    * The URL of resources used for validation.
    * @param resourceUrl The URL of resources used for validation.
    */
   public void setResourceUrl(String resourceUrl) {
      this.resourceUrl = resourceUrl;
   }

   @Override
   public HttpMethod buildMethod(String method) {
      return super.buildMethod(method);
   }

   @Override
   protected void prepareRequest(Request request) {
      startTimestamp = System.currentTimeMillis();
   }

   @Override
   protected int extractTestReturnCode(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse, String responseContent) {
      // Stop timer and initialize code.
      long duration = System.currentTimeMillis() - startTimestamp;
      int code = TestReturn.SUCCESS_CODE;

      // If first request of this runner, we should retrieve resources and cache them.
      if (cachedResources.isEmpty()) {
         List<Resource> resources = resourceRepository.findByServiceId(service.getId());
         for (Resource resource : resources) {
            cachedResources.put(resource.getType(), resource);
         }
      }

      // If first request of this runner, we should parse SoapUI project.
      if (projectElement == null) {
         Resource soapuiProject = cachedResources.get(ResourceType.SOAP_UI_PROJECT);
         try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            projectElement = documentBuilder.parse(new InputSource(new StringReader(soapuiProject.getContent())))
                  .getDocumentElement();
         } catch (Exception e) {
            log.error("Exception while parsing SoapUI resource content {}", soapuiProject.getName(), e);
            return TestReturn.FAILURE_CODE;
         }
      }

      try {
         Map<String, Element> testConfigRequests = collectTestStepsConfigRequest(operation);
         Element testRequest = testConfigRequests.get(getRequestShortName(request.getName()));

         // Now validate the embedded assertions.
         List<Element> assertions = getConfigDirectChildren(testRequest, "assertion");
         for (Element assertion : assertions) {
            String type = assertion.getAttribute("type");
            Map<String, String> configParams = buildParamsMapFromConfiguration(assertion);

            SoapUIAssertion sAssertion = AssertionFactory.intializeAssertion(type, configParams);
            AssertionStatus status = sAssertion.assertResponse(
                  new RequestResponseExchange(request, httpResponse, responseContent, duration),
                  new ExchangeContext(service, operation, List.copyOf(cachedResources.values()), resourceUrl));

            if (status == AssertionStatus.FAILED) {
               code = TestReturn.FAILURE_CODE;
               if (lastValidationErrors == null) {
                  lastValidationErrors = new ArrayList<>();
               }
               lastValidationErrors.addAll(sAssertion.getErrorMessages());
            }
         }
      } catch (MalformedXmlException e) {
         log.error("Exception while parsing SoapUI resource content for assertion on {}", operation.getName(), e);
         return TestReturn.FAILURE_CODE;
      }

      return code;
   }

   @Override
   protected String extractTestReturnMessage(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse) {
      StringBuilder builder = new StringBuilder();
      if (lastValidationErrors != null && !lastValidationErrors.isEmpty()) {
         for (String error : lastValidationErrors) {
            builder.append(error).append('\n');
         }
      }
      // Reset just after consumption so avoid side-effects.
      lastValidationErrors = null;
      return builder.toString();
   }

   private Map<String, Element> collectTestStepsConfigRequest(Operation operation) throws MalformedXmlException {
      Map<String, Element> results = new HashMap<>();

      List<Element> testSuites = getConfigDirectChildren(projectElement, "testSuite");
      for (Element testSuite : testSuites) {
         List<Element> testCases = getConfigDirectChildren(testSuite, "testCase");
         for (Element testCase : testCases) {
            List<Element> testSteps = getConfigDirectChildren(testCase, "testStep");
            for (Element testStep : testSteps) {
               Element config = getConfigUniqueDirectChild(testStep, "config");

               String operationName = null;
               if (hasConfigDirectChild(config, "operation")) {
                  // Soap/Wsdl test request with operation reference.
                  operationName = getConfigUniqueDirectChild(config, "operation").getTextContent();
                  if (operation.getName().equals(operationName)) {
                     results.put(getRequestShortName(testStep.getAttribute("name")),
                           getConfigUniqueDirectChild(config, "request"));
                  }
               } else if (config.hasAttribute("resourcePath")) {
                  // Rest test request with resourcePath as operation reference.
                  operationName = config.getAttribute("resourcePath");
                  if (operation.getName().equals(operationName)) {
                     results.put(getRequestShortName(testStep.getAttribute("name")),
                           SoapUIProjectParserUtils.getConfigUniqueDirectChild(config, "restRequest"));
                  }
               }
            }
         }
      }
      return results;
   }

   /** If a request name ends with " Request", we remove it to get a short name. */
   private String getRequestShortName(String longName) {
      if (longName.endsWith(" Request")) {
         return longName.substring(0, longName.length() - " Request".length());
      }
      return longName;
   }

   private Map<String, String> buildParamsMapFromConfiguration(Element assertion) {
      Map<String, String> params = new HashMap<>();
      if (hasConfigDirectChild(assertion, "configuration")) {
         try {
            Element configuration = getConfigUniqueDirectChild(assertion, "configuration");
            NodeList children = configuration.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
               Node child = children.item(i);
               params.put(child.getLocalName(), child.getTextContent());
            }
         } catch (MalformedXmlException mfe) {
            // Just ignore this as it must not happen.
         }
      }
      return params;
   }
}
