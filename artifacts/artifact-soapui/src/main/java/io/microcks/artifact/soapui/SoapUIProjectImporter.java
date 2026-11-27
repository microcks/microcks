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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MalformedXmlException;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.microcks.util.XmlUtil.WSDL_NS;
import static io.github.microcks.util.XmlUtil.getDirectChildren;
import static io.github.microcks.util.XmlUtil.getUniqueDirectChild;
import static io.github.microcks.util.soapui.SoapUIProjectParserUtils.getConfigDirectChildren;
import static io.github.microcks.util.soapui.SoapUIProjectParserUtils.getConfigUniqueDirectChild;
import static io.github.microcks.util.soapui.SoapUIProjectParserUtils.hasConfigDirectChild;

/**
 * Implement of MockRepositoryImporter that uses a SoapUI project for building domain objects.
 * @author laurent
 */
public class SoapUIProjectImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(SoapUIProjectImporter.class);

   /** SoapUI project property that references service version property. */
   public static final String SERVICE_VERSION_PROPERTY = "version";

   protected static final String NAME_ATTRIBUTE = "name";
   protected static final String ELEMENT_ATTRIBUTE = "element";
   protected static final String MOCK_SERVICE_TAG = "mockService";
   protected static final String REST_MOCK_SERVICE_TAG = "restMockService";
   protected static final String MOCK_OPERATION_TAG = "mockOperation";
   protected static final String REST_MOCK_ACTION_TAG = "restMockAction";
   protected static final String QUERY_TAG = "query";
   protected static final String REQUEST_TAG = "request";
   protected static final String RESPONSE_TAG = "response";
   protected static final String CONTENT_TAG = "content";
   protected static final String VALUE_TAG = "value";

   private final String projectContent;
   private final DocumentBuilder documentBuilder;
   private final Element projectElement;

   private final Map<String, Element> interfaces = new HashMap<>();

   private Element serviceInterface;

   /**
    * Build a new importer.
    * @param projectFilePath The path to local SoapUI project file
    * @throws IOException if project file cannot be found
    */
   public SoapUIProjectImporter(String projectFilePath) throws IOException {
      try {
         // Read project content as string.
         byte[] projectBytes = Files.readAllBytes(Paths.get(projectFilePath));
         projectContent = new String(projectBytes, StandardCharsets.UTF_8);
         // Then parse it to get DOM root Element.
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setNamespaceAware(true);
         documentBuilder = factory.newDocumentBuilder();
         projectElement = documentBuilder.parse(new InputSource(new StringReader(projectContent))).getDocumentElement();
      } catch (Exception e) {
         log.error("Exception while parsing SoapUI file {}", projectFilePath, e);
         throw new IOException("SoapUI project file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      List<Element> interfaceNodes = getConfigDirectChildren(projectElement, "interface");
      for (Element interfaceNode : interfaceNodes) {
         // Filter complete interface definition with name as attribute.
         if (interfaceNode.getAttribute(NAME_ATTRIBUTE) != null) {
            log.info("Found a service interface named: {}", interfaceNode.getAttribute(NAME_ATTRIBUTE));
            interfaces.put(interfaceNode.getAttribute(NAME_ATTRIBUTE), interfaceNode);
            serviceInterface = interfaceNode;
         }
      }

      // Try loading definitions from Soap mock services.
      List<Element> mockServices = getConfigDirectChildren(projectElement, MOCK_SERVICE_TAG);
      if (!mockServices.isEmpty()) {
         result.addAll(getSoapServicesDefinitions(mockServices));
      }
      // Then try loading from Rest mock services.
      List<Element> restMockServices = getConfigDirectChildren(projectElement, REST_MOCK_SERVICE_TAG);
      if (!restMockServices.isEmpty()) {
         result.addAll(getRestServicesDefinitions(restMockServices));
      }

      return result;
   }

   private List<Service> getSoapServicesDefinitions(List<Element> mockServices) throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      try {
         for (Element mockService : mockServices) {
            // Build a new Service.
            Service service = new Service();
            service.setName(mockService.getAttribute(NAME_ATTRIBUTE));
            service.setType(ServiceType.SOAP_HTTP);

            // Check version property that is mandatory.
            Element properties = getConfigUniqueDirectChild(mockService, "properties");
            service.setVersion(extractVersion(properties));

            List<Element> mockOperations = getConfigDirectChildren(mockService, MOCK_OPERATION_TAG);
            for (Element mockOperation : mockOperations) {

               Element interfaceElement = interfaces.get(mockOperation.getAttribute("interface"));
               if (interfaceElement != null) {
                  log.info("Got matching service interface");
                  String bindingQName = interfaceElement.getAttribute("bindingName");
                  service.setXmlNS(extractNSFromQName(bindingQName));
               }

               // Build a new operation from mockOperation.
               Operation operation = new Operation();
               operation.setName(mockOperation.getAttribute(NAME_ATTRIBUTE));

               Element interfaceOperation = getInterfaceOperation(interfaceElement, operation.getName());
               operation.setAction(interfaceOperation.getAttribute("action"));

               try {
                  completeOperationPartsFromWsdl(interfaceElement, operation);
               } catch (Exception e) {
                  // Fallback to input name as a (mostly?) safe default.
                  log.warn(
                        "Was not able to extract element names for input/output payload from WSDL. Defaulting to input and output names.");
               } finally {
                  if (operation.getInputName() == null) {
                     operation.setInputName(interfaceOperation.getAttribute("inputName"));
                  }
                  if (operation.getOutputName() == null) {
                     operation.setOutputName(interfaceOperation.getAttribute("outputName"));
                  }
               }

               Element dispatchStyle = getConfigUniqueDirectChild(mockOperation, "dispatchStyle");
               operation.setDispatcher(dispatchStyle.getTextContent());

               if (DispatchStyles.QUERY_MATCH.equals(operation.getDispatcher())) {
                  // XPath matching rules are under dispatchConfig.query, consider 1st item only.
                  Element dispatchConfig = getConfigUniqueDirectChild(mockOperation, "dispatchConfig");
                  Element firstQuery = getConfigDirectChildren(dispatchConfig, QUERY_TAG).get(0);
                  operation.setDispatcherRules(getConfigUniqueDirectChild(firstQuery, QUERY_TAG).getTextContent());
               } else if (DispatchStyles.SCRIPT.equals(operation.getDispatcher())
                     || DispatchStyles.GROOVY.equals(operation.getDispatcher())
                     || DispatchStyles.JS.equals(operation.getDispatcher())) {
                  // Groovy script is located into dispatchPath element.
                  operation
                        .setDispatcherRules(getConfigUniqueDirectChild(mockOperation, "dispatchPath").getTextContent());
               }

               service.addOperation(operation);
            }
            result.add(service);
         }
      } catch (MalformedXmlException mspe) {
         log.error("Your SoapUI Project seems to be malformed: {}", mspe.getMessage(), mspe);
         throw new MockRepositoryImportException("Your SoapUI Project seems to be malformed: " + mspe.getMessage(),
               mspe);
      }
      return result;
   }

   private void completeOperationPartsFromWsdl(Element interfaceElement, Operation operation) throws Exception {
      Element definitionCache = getConfigUniqueDirectChild(interfaceElement, "definitionCache");
      List<Element> parts = getConfigDirectChildren(definitionCache, "part");

      Element wsdlPart = parts.get(0);
      Element wsdlContent = getConfigUniqueDirectChild(wsdlPart, CONTENT_TAG);
      String wsdlTextContent = wsdlContent.getTextContent();

      Element wsdlDoc = documentBuilder.parse(new InputSource(new StringReader(wsdlTextContent))).getDocumentElement();
      Element binding = getUniqueDirectChild(wsdlDoc, WSDL_NS, "binding");
      List<Element> wsdlOperations = getDirectChildren(binding, WSDL_NS, "operation");
      for (Element wsdlOperation : wsdlOperations) {

         if (operation.getName().equals(wsdlOperation.getAttribute(NAME_ATTRIBUTE))) {
            Element input = getUniqueDirectChild(wsdlOperation, WSDL_NS, "input");
            Element output = getUniqueDirectChild(wsdlOperation, WSDL_NS, "output");
            String inputName = input.getAttribute(NAME_ATTRIBUTE);
            String outputName = output.getAttribute(NAME_ATTRIBUTE);

            List<Element> messages = getDirectChildren(wsdlDoc, WSDL_NS, "message");
            Optional<Element> inputMsg = messages.stream().filter(m -> inputName.equals(m.getAttribute(NAME_ATTRIBUTE)))
                  .findFirst();
            Optional<Element> outputMsg = messages.stream()
                  .filter(m -> outputName.equals(m.getAttribute(NAME_ATTRIBUTE))).findFirst();
            if (inputMsg.isPresent()) {
               Element firstPart = getDirectChildren(inputMsg.get(), WSDL_NS, "part").get(0);
               String localTag = firstPart.getAttribute(ELEMENT_ATTRIBUTE)
                     .substring(firstPart.getAttribute(ELEMENT_ATTRIBUTE).indexOf(":") + 1);
               operation.setInputName(localTag);
            }
            if (outputMsg.isPresent()) {
               Element firstPart = getDirectChildren(outputMsg.get(), WSDL_NS, "part").get(0);
               String localTag = firstPart.getAttribute(ELEMENT_ATTRIBUTE)
                     .substring(firstPart.getAttribute(ELEMENT_ATTRIBUTE).indexOf(":") + 1);
               operation.setOutputName(localTag);
            }
         }
      }
   }

   private List<Service> getRestServicesDefinitions(List<Element> restMockServices)
         throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      try {
         for (Element mockService : restMockServices) {
            // Build a new Service.
            Service service = new Service();
            service.setName(mockService.getAttribute(NAME_ATTRIBUTE));
            service.setType(ServiceType.REST);

            // Check version property that is mandatory.
            Element properties = getConfigUniqueDirectChild(mockService, "properties");
            service.setVersion(extractVersion(properties));

            // Actions corresponding to same operations may be defined multiple times in SoapUI
            // with different resourcePaths. We have to track them to complete them in second step.
            Map<String, Operation> collectedOperations = new HashMap<>();

            List<Element> mockOperations = getConfigDirectChildren(mockService, REST_MOCK_ACTION_TAG);
            for (Element mockOperation : mockOperations) {
               // Check already found operation.
               Operation operation = collectedOperations.get(mockOperation.getAttribute(NAME_ATTRIBUTE));

               if (operation == null) {
                  // Build a new operation from mockRestAction.
                  operation = new Operation();
                  operation.setName(mockOperation.getAttribute(NAME_ATTRIBUTE));
                  operation.setMethod(mockOperation.getAttribute("method"));

                  Element dispatchStyle = getConfigUniqueDirectChild(mockOperation, "dispatchStyle");
                  operation.setDispatcher(dispatchStyle.getTextContent());

                  if (DispatchStyles.SEQUENCE.equals(operation.getDispatcher())) {
                     // Extract simple dispatcher rules from operation name.
                     operation
                           .setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(operation.getName()));
                  } else if (DispatchStyles.SCRIPT.equals(operation.getDispatcher())
                        || DispatchStyles.GROOVY.equals(operation.getDispatcher())
                        || DispatchStyles.JS.equals(operation.getDispatcher())) {
                     // Groovy script is located into dispatchPath element.
                     operation.setDispatcherRules(
                           getConfigUniqueDirectChild(mockOperation, "dispatchPath").getTextContent());
                  }
                  service.addOperation(operation);
               }
               // Add this configuration resource path.
               operation.addResourcePath(mockOperation.getAttribute("resourcePath"));
               collectedOperations.put(mockOperation.getAttribute("name"), operation);
            }
            result.add(service);
         }
      } catch (MalformedXmlException mspe) {
         log.error("Your SoapUI Project seems to be malformed: {}", mspe.getMessage(), mspe);
         throw new MockRepositoryImportException("Your SoapUI Project seems to be malformed: " + mspe.getMessage(),
               mspe);
      }
      return result;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();

      // First record the project itself as an artifact.
      Resource projectResource = new Resource();
      projectResource.setName(service.getName() + "-" + service.getVersion() + ".xml");
      projectResource.setType(ResourceType.SOAP_UI_PROJECT);
      projectResource.setContent(projectContent);
      results.add(projectResource);

      try {
         Element definitionCache = getConfigUniqueDirectChild(serviceInterface, "definitionCache");
         List<Element> parts = getConfigDirectChildren(definitionCache, "part");

         if (!parts.isEmpty()) {
            Element wsdlPart = parts.get(0);
            Element wsdlContent = getConfigUniqueDirectChild(wsdlPart, CONTENT_TAG);
            String wsdlTextContent = wsdlContent.getTextContent();

            for (int i = 1; i < parts.size(); i++) {
               Element xsdPart = parts.get(i);
               String xsdUrl = getConfigUniqueDirectChild(xsdPart, "url").getTextContent().trim();
               String xsdName = xsdUrl.substring(xsdUrl.lastIndexOf('/') + 1);
               // Try also Windows style path separators.
               if (xsdUrl.contains("\\")) {
                  xsdName = xsdUrl.substring(xsdUrl.lastIndexOf("\\") + 1);
               }

               String xsdContent = getConfigUniqueDirectChild(xsdPart, CONTENT_TAG).getTextContent();

               Resource xsdResource = new Resource();
               xsdResource.setName(xsdName);
               xsdResource.setType(ResourceType.XSD);
               xsdResource.setContent(xsdContent);
               results.add(xsdResource);

               // URL references within WSDL must be replaced by their local counterpart.
               wsdlTextContent = wsdlTextContent.replace(xsdUrl, "./" + xsdName);
               // TODO: have a in-depth review on how xsd are actually resolved (and should probably be fixed)
               // wsdlTextContent = wsdlTextContent.replaceAll("schemaLocation=\"(.*)\\/(" + xsdName + ")", "schemaLocation=\"./" + xsdName + "\"");
            }

            Resource wsdlResource = new Resource();
            wsdlResource.setName(service.getName() + "-" + service.getVersion() + ".wsdl");
            wsdlResource.setType(ResourceType.WSDL);
            wsdlResource.setContent(wsdlTextContent);
            results.add(wsdlResource);
         }
      } catch (MalformedXmlException mxe) {
         log.warn("Got a MalformedXmlException while trying to extract WSDL and XSD: {}", mxe.getMessage());
         log.warn("Just failing silently as it's not a critical stuff in SoapUI implementation");
      }

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      List<Exchange> results = new ArrayList<>();

      if (ServiceType.SOAP_HTTP == service.getType()) {
         results.addAll(getSoapMessageDefinitions(service, operation));
      } else if (ServiceType.REST == service.getType()) {
         results.addAll(getRestMessageDefinitions(service, operation));
      }
      return results;
   }

   private List<Exchange> getSoapMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      Map<Request, Response> result = new HashMap<>();
      try {
         List<Element> mockServices = getConfigDirectChildren(projectElement, MOCK_SERVICE_TAG);
         for (Element mockService : mockServices) {
            // Find the appropriate mock service.
            if (service.getName().equals(mockService.getAttribute(NAME_ATTRIBUTE))) {

               List<Element> mockOperations = getConfigDirectChildren(mockService, MOCK_OPERATION_TAG);
               for (Element mockOperation : mockOperations) {
                  // Find the appropriate mock service operation.
                  if (operation.getName().equals(mockOperation.getAttribute("operation"))) {
                     // Collect available test requests for this operation.
                     Map<String, Element> availableRequests = collectTestStepsRequests(operation);
                     // Then filter only those that are candidates to mock response matching.
                     List<Element> candidateRequests = new ArrayList<>();

                     List<Element> mockResponses = getConfigDirectChildren(mockOperation, RESPONSE_TAG);
                     for (Element mockResponse : mockResponses) {
                        String responseName = mockResponse.getAttribute(NAME_ATTRIBUTE);
                        Element matchingRequest = availableRequests.get(responseName);
                        if (matchingRequest == null) {
                           matchingRequest = availableRequests.get(responseName + " Request");
                        }
                        if (matchingRequest == null && responseName.contains("Response")) {
                           matchingRequest = availableRequests.get(responseName.replace("Response", "Request"));
                        }

                        if (matchingRequest == null) {
                           log.warn("No request found for response '{}' into SoapUI project '{}'", responseName,
                                 projectElement.getAttribute("name"));
                           continue;
                        }
                        candidateRequests.add(matchingRequest);
                     }

                     if (DispatchStyles.QUERY_MATCH.equals(operation.getDispatcher())) {
                        // Browse candidates and apply query dispatcher criterion to find corresponding response.
                        try {
                           XPathExpression xpath = initializeXPathMatcher(operation);

                           Map<String, String> matchToResponseMap = buildQueryMatchDispatchCriteriaToResponseMap(
                                 mockOperation);
                           for (Element candidateRequest : candidateRequests) {
                              // Evaluate matcher against request and get name of corresponding response.
                              String requestContent = getConfigUniqueDirectChild(candidateRequest, REQUEST_TAG)
                                    .getTextContent();
                              String dispatchCriteria = xpath
                                    .evaluate(new InputSource(new StringReader(requestContent)));
                              String correspondingResponse = matchToResponseMap.get(dispatchCriteria);

                              Element matchingResponse = getMockResponseByName(mockOperation, correspondingResponse);
                              if (matchingResponse != null) {
                                 // Build response from MockResponse and response from matching one.
                                 Response response = buildResponse(matchingResponse, dispatchCriteria);
                                 Request request = buildRequest(candidateRequest);
                                 result.put(request, response);
                              }
                           }
                        } catch (XPathExpressionException e) {
                           throw new RuntimeException(e);
                        }
                     } else if (DispatchStyles.SCRIPT.equals(operation.getDispatcher())
                           || DispatchStyles.GROOVY.equals(operation.getDispatcher())
                           || DispatchStyles.JS.equals(operation.getDispatcher())) {
                        for (Element candidateRequest : candidateRequests) {
                           Element mockResponse = getMockResponseByName(mockOperation,
                                 candidateRequest.getAttribute(NAME_ATTRIBUTE));
                           if (mockResponse == null
                                 && candidateRequest.getAttribute(NAME_ATTRIBUTE).contains("Request")) {
                              mockResponse = getMockResponseByName(mockOperation,
                                    candidateRequest.getAttribute(NAME_ATTRIBUTE).replace(" Request", " Response"));
                           }

                           if (mockResponse == null) {
                              log.warn("No response found for request {} into SoapUI project {}",
                                    candidateRequest.getAttribute(NAME_ATTRIBUTE),
                                    projectElement.getAttribute(NAME_ATTRIBUTE));
                              continue;
                           }

                           // Build response from MockResponse and response from matching one.
                           Response response = buildResponse(mockResponse, mockResponse.getAttribute(NAME_ATTRIBUTE));
                           Request request = buildRequest(candidateRequest);
                           result.put(request, response);
                        }
                     } else if (DispatchStyles.RANDOM.equals(operation.getDispatcher())) {
                        if (availableRequests.isEmpty()) {
                           log.warn(
                                 "A request is mandatory even for a RANDOM dispatch. Operation {} into SoapUI project  {}",
                                 operation.getName(), projectElement.getAttribute(NAME_ATTRIBUTE));
                        } else {
                           // Use the first one for all the responses
                           Element mockRequest = availableRequests.values().iterator().next();

                           for (Element mockResponse : mockResponses) {
                              // Build response from MockResponse and response from matching one.
                              Response response = buildResponse(mockResponse, DispatchStyles.RANDOM);
                              Request request = buildRequest(mockRequest);
                              request.setName(operation.getName());
                              result.put(request, response);
                           }
                        }
                     }
                     break;
                  }
               }
               break;
            }
         }
      } catch (Throwable t) {
         throw new MockRepositoryImportException(t.getMessage());
      }

      // Adapt map to list of Exchanges.
      return result.entrySet().stream().map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
   }

   private List<Exchange> getRestMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      Map<Request, Response> result = new HashMap<>();
      try {
         List<Element> mockServices = getConfigDirectChildren(projectElement, REST_MOCK_SERVICE_TAG);
         for (Element mockService : mockServices) {
            // Find the appropriate mock service.
            if (service.getName().equals(mockService.getAttribute(NAME_ATTRIBUTE))) {

               List<Element> mockOperations = getConfigDirectChildren(mockService, REST_MOCK_ACTION_TAG);
               for (Element mockOperation : mockOperations) {
                  // Find the appropriate mock service operation.
                  if (operation.getName().equals(mockOperation.getAttribute(NAME_ATTRIBUTE))) {

                     // Collect available test requests for this operation.
                     Map<String, Element> availableRequests = collectTestStepsRestRequests(operation);
                     // Collection also mock responses that are sparsly dispatched under mockRestActions having the name same.
                     List<Element> mockResponses = getMockRestResponses(mockService, operation);

                     // Then filter only those that are matching with a mock response.
                     Map<Element, Element> requestToResponses = new HashMap<>();

                     for (Element mockResponse : mockResponses) {
                        String responseName = mockResponse.getAttribute(NAME_ATTRIBUTE);
                        Element matchingRequest = availableRequests.get(responseName);
                        if (matchingRequest == null) {
                           matchingRequest = availableRequests.get(responseName + " Request");
                        }
                        if (matchingRequest == null && responseName.contains("Response")) {
                           matchingRequest = availableRequests.get(responseName.replace("Response", "Request"));
                        }

                        if (matchingRequest == null) {
                           log.warn("No request found for response '{}' into SoapUI project '{}'", responseName,
                                 projectElement.getAttribute("name"));
                           continue;
                        }
                        requestToResponses.put(matchingRequest, mockResponse);
                     }

                     for (Map.Entry<Element, Element> entry : requestToResponses.entrySet()) {
                        String dispatchCriteria = null;

                        if (DispatchStyles.SEQUENCE.equals(operation.getDispatcher())) {
                           // Build a dispatch criteria from operation name projected onto resourcePath pattern
                           // eg. /deployment/byComponent/{component}/{version}.json  => /deployment/byComponent/testREST/1.2.json
                           // for producing /component=testREST/version=1.2
                           // resourcePath is actually available in restMockAction wrapping response. Navigate to parent.
                           String resourcePath = ((Element) entry.getValue().getParentNode())
                                 .getAttribute("resourcePath");
                           dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(
                                 operation.getDispatcherRules(), operation.getName(), resourcePath);
                        } else if (DispatchStyles.SCRIPT.equals(operation.getDispatcher())
                              || DispatchStyles.GROOVY.equals(operation.getDispatcher())
                              || DispatchStyles.JS.equals(operation.getDispatcher())) {
                           // Build a dispatch criteria that is equal to response name (that script evaluation should return...)
                           dispatchCriteria = entry.getValue().getAttribute(NAME_ATTRIBUTE);
                        }

                        Response response = buildResponse(entry.getValue(), dispatchCriteria);
                        Request request = buildRequest(entry.getKey());
                        result.put(request, response);
                     }
                     break;
                  }
               }
            }
         }
      } catch (Throwable t) {
         throw new MockRepositoryImportException(t.getMessage());
      }

      // Adapt map to list of Exchanges.
      return result.entrySet().stream().map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
   }

   private String extractNSFromQName(String qName) {
      if (qName.startsWith("{") && qName.indexOf("}") > 1) {
         return qName.substring(1, qName.indexOf("}"));
      }
      return qName;
   }

   /** Extract version from mockService properties. */
   private String extractVersion(Element properties) throws MockRepositoryImportException, MalformedXmlException {
      List<Element> propertyList = getConfigDirectChildren(properties, "property");
      for (Element property : propertyList) {
         Element propertyName = getConfigUniqueDirectChild(property, NAME_ATTRIBUTE);
         Element propertyValue = getConfigUniqueDirectChild(property, VALUE_TAG);
         if (SERVICE_VERSION_PROPERTY.equals(propertyName.getTextContent())) {
            return propertyValue.getTextContent();
         }
      }
      log.error("Version property is missing in Project properties");
      throw new MockRepositoryImportException("Version property is missing in Project properties");
   }

   private Element getInterfaceOperation(Element serviceInterface, String operationName)
         throws MockRepositoryImportException {
      List<Element> operations = getConfigDirectChildren(serviceInterface, "operation");
      for (Element operation : operations) {
         if (operationName.equals(operation.getAttribute(NAME_ATTRIBUTE))) {
            return operation;
         }
      }
      log.error("Operation {} is missing into Service interface", operationName);
      throw new MockRepositoryImportException("Operation " + operationName + " is missing into Service interface");
   }

   private Map<String, Element> collectTestStepsRequests(Operation operation) throws MalformedXmlException {
      Map<String, Element> results = new HashMap<>();

      List<Element> testSuites = getConfigDirectChildren(projectElement, "testSuite");
      for (Element testSuite : testSuites) {
         List<Element> testCases = getConfigDirectChildren(testSuite, "testCase");
         for (Element testCase : testCases) {
            List<Element> testSteps = getConfigDirectChildren(testCase, "testStep");
            for (Element testStep : testSteps) {

               Element config = getConfigUniqueDirectChild(testStep, "config");
               String interfaceName = getConfigUniqueDirectChild(config, "interface").getTextContent();
               String operationName = getConfigUniqueDirectChild(config, "operation").getTextContent();

               if (operation.getName().equals(operationName)) {
                  results.put(testStep.getAttribute(NAME_ATTRIBUTE), getConfigUniqueDirectChild(config, REQUEST_TAG));
               }
            }
         }
      }
      return results;
   }

   private Map<String, Element> collectTestStepsRestRequests(Operation operation) throws MalformedXmlException {
      Map<String, Element> results = new HashMap<>();

      List<Element> testSuites = getConfigDirectChildren(projectElement, "testSuite");
      for (Element testSuite : testSuites) {
         List<Element> testCases = getConfigDirectChildren(testSuite, "testCase");
         for (Element testCase : testCases) {
            List<Element> testSteps = getConfigDirectChildren(testCase, "testStep");
            for (Element testStep : testSteps) {
               Element config = getConfigUniqueDirectChild(testStep, "config");
               String operationName = config.getAttribute("resourcePath");
               if (operation.getName().equals(operationName)) {
                  results.put(testStep.getAttribute(NAME_ATTRIBUTE), getConfigUniqueDirectChild(config, "restRequest"));
               } else {
                  // If this artifact is second one and OpenAPI was the main one, operationName may
                  // start with the verb. We have to extract the verb from elsewhere.
                  String methodName = config.getAttribute("methodName");
                  Optional<Element> method = getRestInterfaceResourceMethod(operationName, methodName);
                  if (method.isPresent()) {
                     String methodString = method.get().getAttribute("method");
                     if (operation.getName().equals(methodString.toUpperCase() + " " + operationName)) {
                        results.put(testStep.getAttribute(NAME_ATTRIBUTE),
                              getConfigUniqueDirectChild(config, "restRequest"));
                     }
                  }
               }
            }
         }
      }
      return results;
   }

   private Optional<Element> getRestInterfaceResourceMethod(String resourcePath, String methodName)
         throws MalformedXmlException {
      return getConfigDirectChildren(serviceInterface, "resource").stream()
            .filter(resource -> resourcePath.equals(resource.getAttribute("path")))
            .flatMap(resource -> getConfigDirectChildren(resource, "method").stream())
            .filter(method -> methodName.equals(method.getAttribute("name"))).findFirst();
   }

   private List<Element> getMockRestResponses(Element restMockService, Operation operation) {
      List<Element> responses = new ArrayList<>();
      List<Element> mockActions = getConfigDirectChildren(restMockService, REST_MOCK_ACTION_TAG);
      for (Element mockAction : mockActions) {
         if (operation.getName().equals(mockAction.getAttribute(NAME_ATTRIBUTE))) {
            responses.addAll(getConfigDirectChildren(mockAction, RESPONSE_TAG));
         }
      }
      return responses;
   }

   /** Build a XPathMatcher based on operation dispatcher rules. */
   private XPathExpression initializeXPathMatcher(Operation operation) throws XPathExpressionException {
      return SoapUIXPathBuilder.buildXPathMatcherFromRules(operation.getDispatcherRules());
   }

   private Map<String, String> buildQueryMatchDispatchCriteriaToResponseMap(Element mockOperation)
         throws MalformedXmlException {
      Map<String, String> matchResponseMap = new HashMap<>();

      String dispatcher = getConfigUniqueDirectChild(mockOperation, "dispatchStyle").getTextContent();
      if (DispatchStyles.QUERY_MATCH.equals(dispatcher)) {
         Element dispatchConfig = getConfigUniqueDirectChild(mockOperation, "dispatchConfig");

         List<Element> queries = SoapUIProjectParserUtils.getConfigDirectChildren(dispatchConfig, QUERY_TAG);
         for (Element query : queries) {
            String match = getConfigUniqueDirectChild(query, "match").getTextContent();
            String response = getConfigUniqueDirectChild(query, RESPONSE_TAG).getTextContent();
            matchResponseMap.put(match, response);
         }
      }
      return matchResponseMap;
   }

   private Element getMockResponseByName(Element mockOperation, String responseName) {
      List<Element> responses = getConfigDirectChildren(mockOperation, RESPONSE_TAG);
      for (Element response : responses) {
         if (responseName.equals(response.getAttribute(NAME_ATTRIBUTE))) {
            return response;
         }
      }
      return null;
   }

   private Response buildResponse(Element mockResponse, String dispatchCriteria)
         throws MockRepositoryImportException, Exception {
      Response response = new Response();
      response.setName(mockResponse.getAttribute(NAME_ATTRIBUTE));
      response.setContent(getConfigUniqueDirectChild(mockResponse, "responseContent").getTextContent());
      response.setHeaders(buildHeaders(mockResponse));
      response.setDispatchCriteria(dispatchCriteria);
      response.setStatus(mockResponse.getAttribute("httpResponseStatus"));
      if ("500".equals(response.getStatus())) {
         response.setFault(true);
      }
      response.setMediaType(mockResponse.getAttribute("mediaType"));
      return response;
   }

   private Request buildRequest(Element testRequest) throws MockRepositoryImportException, Exception {
      Request request = new Request();
      request.setName(testRequest.getAttribute(NAME_ATTRIBUTE));
      if (hasConfigDirectChild(testRequest, REQUEST_TAG)) {
         request.setContent(getConfigUniqueDirectChild(testRequest, REQUEST_TAG).getTextContent());
      }
      request.setHeaders(buildHeaders(testRequest));

      // Add query parameters only if presents.
      if (hasConfigDirectChild(testRequest, "parameters")) {
         List<Element> entries = getConfigDirectChildren(getConfigUniqueDirectChild(testRequest, "parameters"),
               "entry");
         for (Element entry : entries) {
            Parameter param = new Parameter();
            param.setName(entry.getAttribute("key"));
            param.setValue(entry.getAttribute(VALUE_TAG));
            request.addQueryParameter(param);
         }
      }
      return request;
   }

   private Set<Header> buildHeaders(Element requestOrResponse) throws Exception {
      if (hasConfigDirectChild(requestOrResponse, "settings")) {
         Element settingsElt = getConfigUniqueDirectChild(requestOrResponse, "settings");
         List<Element> settings = getConfigDirectChildren(settingsElt, "setting");
         for (Element setting : settings) {
            if (setting.getAttribute("id").contains("@request-headers")) {

               // 2 possibilities here:
               // 1) &lt;xml-fragment xmlns:con="http://eviware.com/soapui/config">
               //      &lt;con:entry key="Authorization" value="Basic YWRtaW46YWRtaW4="/>
               //      &lt;con:entry key="x-userid" value="s026210"/>
               //    &lt;/xml-fragment>
               // 2) &lt;entry key="toto"
               //                value="tata" xmlns="http://eviware.com/soapui/config"/>
               String headersContent = setting.getTextContent();
               Element fragment = documentBuilder.parse(new InputSource(new StringReader(headersContent)))
                     .getDocumentElement();

               Set<Header> headers = new HashSet<>();
               if (headersContent.contains("xml-fragment")) {
                  List<Element> entries = getConfigDirectChildren(fragment, "entry");
                  for (Element entry : entries) {
                     Header header = new Header();
                     header.setName(entry.getAttribute("key"));
                     header.setValues(Set.of(entry.getAttribute(VALUE_TAG)));
                     headers.add(header);
                  }
               } else {
                  Header header = new Header();
                  header.setName(fragment.getAttribute("key"));
                  header.setValues(Set.of(fragment.getAttribute(VALUE_TAG)));
                  headers.add(header);
               }
               return headers;
            }
         }
      }
      return null;
   }
}
