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
package io.github.microcks.util.soapui;

import com.eviware.soapui.config.DefintionPartConfig;
import com.eviware.soapui.config.RESTMockActionConfig;
import com.eviware.soapui.config.RESTMockResponseConfig;
import com.eviware.soapui.impl.rest.mock.RestMockResponse;
import com.eviware.soapui.impl.rest.mock.RestMockService;
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder;
import com.eviware.soapui.impl.wsdl.WsdlInterface;
import com.eviware.soapui.impl.wsdl.WsdlOperation;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.mock.WsdlMockOperation;
import com.eviware.soapui.impl.wsdl.mock.WsdlMockResponse;
import com.eviware.soapui.impl.wsdl.mock.WsdlMockService;
import com.eviware.soapui.impl.wsdl.mock.dispatch.MockOperationDispatcher;
import com.eviware.soapui.impl.wsdl.mock.dispatch.QueryMatchMockOperationDispatcher;
import com.eviware.soapui.impl.wsdl.mock.dispatch.ScriptMockOperationDispatcher;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep;
import com.eviware.soapui.model.mock.MockOperation;
import com.eviware.soapui.model.mock.MockResponse;
import com.eviware.soapui.model.mock.MockService;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.support.types.StringToStringsMap;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Implement of MockRepositoryImporter that uses a SoapUI project for building
 * domain objects.
 * @author laurent
 */
public class SoapUIProjectImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapUIProjectImporter.class);

   /** SoapUI project property that references service version property. */
   public static final String SERVICE_VERSION_PROPERTY = "version";

   private WsdlProject project;

   /**
    * Build a new importer.
    * @param projectFilePath The path to local SoapUI project file
    * @throws IOException if project file cannot be found
    */
   public SoapUIProjectImporter(String projectFilePath) throws IOException{
      try{
         project = new WsdlProject(projectFilePath);
      } catch (Exception e) {
         log.error("Exception while parsing SoapUI file " + projectFilePath, e);
         throw new IOException("SoapUI project file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();
      // Add Soap and then Rest services definitions.
      result.addAll(getSoapServiceDefinitions(project.getMockServiceList()));
      result.addAll(getRestServiceDefinitions(project.getRestMockServiceList()));
      return result;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();

      // For now, only available for Wsdl based projects having mocked interfaces.
      WsdlMockService wsdlMockService = project.getMockServiceByName(service.getName());

      if (wsdlMockService != null){
         // Use only the fisrt interface of the mock service corresponding to service.
         WsdlInterface wi = project.getMockServiceByName(service.getName()).getMockedInterfaces()[0];

         List<DefintionPartConfig> parts = wi.getConfig().getDefinitionCache().getPartList();

         if (parts != null && parts.size() > 0){
            // First part is always the wsdl definition, get its content as string.
            String wsdlContent = parts.get(0).getContent().newCursor().getTextValue();

            // Then browse the following one (XSD) and change relative path in imports.
            for (int i=1; i<parts.size(); i++){
               DefintionPartConfig xsdConfig = parts.get(i);
               String xsdUrl = xsdConfig.getUrl();
               String xsdName = xsdUrl.substring(xsdUrl.lastIndexOf('/') + 1);
               String xsdContent = xsdConfig.getContent().newCursor().getTextValue();

               // Build a new xsd resource for this part.
               Resource xsdResource = new Resource();
               xsdResource.setName(xsdName);
               xsdResource.setType(ResourceType.XSD);
               xsdResource.setContent(xsdContent);
               results.add(xsdResource);

               // URL references within WSDL must be replaced by their local counterpart.
               wsdlContent = wsdlContent.replace(xsdUrl, "./" + xsdName);
            }

            // Finally, declare englobing wsdl resource.
            Resource wsdlResource = new Resource();
            wsdlResource.setName(service.getName() + "-" + service.getVersion() + ".wsdl");
            wsdlResource.setType(ResourceType.WSDL);
            wsdlResource.setContent(wsdlContent);
            results.add(wsdlResource);
         }
      }
      return results;
   }

   @Override
   public Map<Request, Response> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException {
      // First try with a Soap Service mock...
      MockService mockService = project.getMockServiceByName(service.getName());
      if (mockService != null){
         try {
            return getSoapMessageDefinitions(mockService, operation);
         } catch (XPathExpressionException xpe) {
            log.error("Got a XPathExpressionException while retrieving soap messages", xpe);
            throw new MockRepositoryImportException("XPathExpressionExcpetion while retrieving soap messages", xpe);
         }
      }
      // ... then with a Rest Service mock.
      RestMockService restMockService = project.getRestMockServiceByName(service.getName());
      if (restMockService != null){
         return getRestMessageDefinitions(restMockService, operation);
      }
      return new HashMap<>();
   }


   /**
    * Get the definitions of Soap Services from mock services.
    */
   private List<Service> getSoapServiceDefinitions(List<WsdlMockService> mockServices) throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      for (WsdlMockService wms : mockServices){
         // First assume it's a Soap one.
         Service service = new Service();
         service.setName(wms.getName());
         service.setType(ServiceType.SOAP_HTTP);

         // Ensure we've got only one interface and extract its namespace.
         WsdlInterface[] wi = wms.getMockedInterfaces();
         if (wi == null || wi.length > 1){
            // TODO Throw a typed exception here...
         }
         service.setXmlNS(wi[0].getBindingName().getNamespaceURI());

         // Extract version from custom properties.
         String version = wms.getPropertyValue(SERVICE_VERSION_PROPERTY);
         if (version == null){
            log.error("Version property is missing in Project properties");
            throw new MockRepositoryImportException("Version property is missing in Project properties");
         }
         service.setVersion(version);

         // Then build its operations.
         service.setOperations(extractOperations(wms, wi[0]));
         result.add(service);
      }
      return result;
   }

   /**
    * Get the definitions of Rest Services from mock services.
    */
   private List<Service> getRestServiceDefinitions(List<RestMockService> mockServices){
      List<Service> result = new ArrayList<>();

      for (RestMockService rms : mockServices) {
         // First assume it's a Rest one for now.
         Service service = new Service();
         service.setName(rms.getName());
         service.setType(ServiceType.REST);

         // Extract version from custom properties.
         String version = rms.getPropertyValue(SERVICE_VERSION_PROPERTY);
         if (version == null){
            // TODO Throw a typed exception here...
         }
         service.setVersion(version);

         // Then build its operations.
         service.setOperations(extractOperations(rms));
         result.add(service);
      }
      return result;
   }

   /**
    * Extract the list of operations from MockService according WsdlInterface.
    */
   private List<Operation> extractOperations(MockService mockService, WsdlInterface wi){
      List<Operation> result = new ArrayList<Operation>();

      List<MockOperation> operations = mockService.getMockOperationList();
      for (MockOperation mockOperation : operations){
         // Build a new operation.
         Operation operation = new Operation();
         operation.setName(mockOperation.getName());

         // Retrieve part name from Wsdl operation coming from interface.
         WsdlOperation wo = wi.getOperationByName(mockOperation.getName());
         operation.setInputName(wo.getInputName());
         operation.setOutputName(wo.getOutputName());

         WsdlMockOperation wmo = (WsdlMockOperation)mockOperation;
         operation.setDispatcher(wmo.getDispatchStyle());

         // Check dispatcher configuration.
         MockOperationDispatcher dispatcher = wmo.getDispatcher();
         if (dispatcher instanceof QueryMatchMockOperationDispatcher){
            QueryMatchMockOperationDispatcher qmDispatcher = (QueryMatchMockOperationDispatcher) dispatcher;
            String query = qmDispatcher.getQueryAt(0).getQuery();
            operation.setDispatcherRules(query);
         }
         else if (dispatcher instanceof ScriptMockOperationDispatcher){
            ScriptMockOperationDispatcher sDispatcher = (ScriptMockOperationDispatcher) dispatcher;
            String script = sDispatcher.getMockOperation().getScript();
            operation.setDispatcherRules(script);
         }

         result.add(operation);
      }
      return result;
   }

   /**
    * Extract the list of operations from RestMockService.
    */
   private List<Operation> extractOperations(RestMockService mockService){
      // Actions corresponding to same operations may be defined multiple times in SoapUI
      // with different resourcePaths. We have to track them to complete them in second step.
      Map<String, Operation> collectedOperations = new HashMap<String, Operation>();

      List<RESTMockActionConfig> actions = mockService.getConfig().getRestMockActionList();
      for (RESTMockActionConfig action : actions){
         // Check already found operation.
         Operation operation = collectedOperations.get(action.getName());

         if (operation == null){
            // Build a new operation.
            operation = new Operation();
            operation.setName(action.getName());

            // Complete with REST specific fields.
            operation.setMethod(action.getMethod());

            // Deal with dispatcher stuffs.
            operation.setDispatcher(action.getDispatchStyle().toString());

            if (DispatchStyles.SEQUENCE.equals(action.getDispatchStyle().toString())){
               operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(operation.getName()));
            }
            else if (DispatchStyles.SCRIPT.equals(action.getDispatchStyle().toString())){
               String script = action.getDispatchPath();
               operation.setDispatcherRules(script);
            }
         }
         // Add this configuration resource path.
         operation.addResourcePath(action.getResourcePath());
         collectedOperations.put(action.getName(), operation);
      }
      return new ArrayList<>(collectedOperations.values());
   }

   /**
    * Get message definition for an operation of a Soap mock service.
    */
   private Map<Request, Response> getSoapMessageDefinitions(MockService mockService, Operation operation) throws XPathExpressionException{
      Map<Request, Response> result = new HashMap<Request, Response>();

      // Get MockOperation corresponding to operation.
      MockOperation mockOperation = mockService.getMockOperationByName(operation.getName());

      // Collect available test requests for this operation.
      Map<String, WsdlTestRequest> availableRequests = collectWsdlTestRequests(operation);

      // Then filter only those that are candidates to mock response matching.
      List<WsdlTestRequest> requests = new ArrayList<WsdlTestRequest>();
      for (MockResponse mockResponse : mockOperation.getMockResponses()){

         // Check if there's a corresponding request in test cases.
         WsdlTestRequest matchingRequest = availableRequests.get(mockResponse.getName());
         if (matchingRequest == null){
            matchingRequest = availableRequests.get(mockResponse.getName() + " Request");
         }
         if (matchingRequest == null && mockResponse.getName().contains("Response")){
            matchingRequest = availableRequests.get(mockResponse.getName().replace("Response", "Request"));
         }

         if (matchingRequest == null){
            log.warn("No request found for response " + mockResponse.getName() + " into SoapUI project " + project.getName());
            continue;
         }
         requests.add(matchingRequest);
      }

      if (DispatchStyles.QUERY_MATCH.equals(operation.getDispatcher())){
         // Browse candidates and apply query dispatcher criterion to find corresponding response.
         XPathExpression xpath = initializeXPathMatcher(operation);
         Map<String, String> matchToResponseMap = buildQueryMatchDispatchCriteriaToResponseMap((WsdlMockOperation)mockOperation);

         for (WsdlTestRequest wtr : requests){
            // Evaluate matcher against request and get name of corresponding response.
            String dispatchCriteria = xpath.evaluate(new InputSource(new StringReader(wtr.getRequestContent())));
            String correspondingResponse = matchToResponseMap.get(dispatchCriteria);

            MockResponse mockResponse = mockOperation.getMockResponseByName(correspondingResponse);

            if (mockResponse != null){
               // Build response from MockResponse and response from matching one
               Response response = buildResponse(mockResponse, dispatchCriteria);
               Request request = buildRequest(wtr);
               result.put(request, response);
            }
         }
      }
      else if (DispatchStyles.SCRIPT.equals(operation.getDispatcher())){
         for (WsdlTestRequest wtr : requests){
            MockResponse mockResponse = mockOperation.getMockResponseByName(wtr.getName().replace(" Request", ""));
            if (mockResponse == null && wtr.getName().contains("Request")){
               mockResponse = mockOperation.getMockResponseByName(wtr.getName().replace(" Request", " Response"));
            }

            if (mockResponse == null){
               log.warn("No response found for request " + wtr.getName() + " into SoapUI project " + project.getName());
               continue;
            }

            // Build response from MockResponse and response from matching one.
            Response response = buildResponse(mockResponse, mockResponse.getName());
            Request request = buildRequest(wtr);
            result.put(request, response);
         }
      }
      return result;
   }

   /**
    * Get message definition for an operation of a Rest mock service.
    */
   private Map<Request, Response> getRestMessageDefinitions(RestMockService mockService, Operation operation){
      Map<Request, Response> result = new HashMap<Request, Response>();

      // Collect mock responses for MockOperation corresponding to operation (it may be many).
      List<MockResponse> mockResponses = new ArrayList<MockResponse>();
      for (MockOperation mockOperation : mockService.getMockOperationList()){
         if (mockOperation.getName().equals(operation.getName())){
            mockResponses.addAll(mockOperation.getMockResponses());
         }
      }

      // Collect also mock action configs, organizing them within a map with response name as key.
      Map<String, RESTMockActionConfig> mockRestActionConfigsForResponses = new HashMap<String, RESTMockActionConfig>();
      for (RESTMockActionConfig mockActionConfig : mockService.getConfig().getRestMockActionList()){
         if (mockActionConfig.getName().equals(operation.getName())){
            for (RESTMockResponseConfig mockRestResponse : mockActionConfig.getResponseList()){
               mockRestActionConfigsForResponses.put(mockRestResponse.getName(), mockActionConfig);
            }
         }
      }

      // Collect available test requests for this operation.
      Map<String, RestTestRequest> availableRequests = collectRestTestRequests(operation);

      // Then find only those that are candidates to mock response matching.
      Map<RestTestRequest, MockResponse> requestToResponse = new HashMap<RestTestRequest, MockResponse>();
      for (MockResponse mockResponse : mockResponses){

         // Check if there's a corresponding request in test cases.
         RestTestRequest matchingRequest = availableRequests.get(mockResponse.getName());
         if (matchingRequest == null){
            matchingRequest = availableRequests.get(mockResponse.getName() + " Request");
         }
         if (matchingRequest == null && mockResponse.getName().contains("Response")){
            matchingRequest = availableRequests.get(mockResponse.getName().replace("Response", "Request"));
         }

         if (matchingRequest == null){
            log.warn("No request found for response " + mockResponse.getName() + " into SoapUI project " + project.getName());
            continue;
         }
         requestToResponse.put(matchingRequest, mockResponse);
      }

      for (Map.Entry<RestTestRequest, MockResponse> entry : requestToResponse.entrySet()){
         RestTestRequest rtr = entry.getKey();
         MockResponse mr = entry.getValue();

         String dispatchCriteria = null;

         if (DispatchStyles.SEQUENCE.equals(operation.getDispatcher())){
            // Build a dispatch criteria from operation name projected onto resourcePath pattern
            // eg. /deployment/byComponent/{component}/{version}.json  => /deployment/byComponent/testREST/1.2.json
            // for producing /component=testREST/version=1.2
            RESTMockActionConfig actionConfig = mockRestActionConfigsForResponses.get(mr.getName());
            dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getName(), actionConfig.getResourcePath());
         }
         else if (DispatchStyles.SCRIPT.equals(operation.getDispatcher())){
            // Build a dispatch criteria that is equal to response name (that script evaluation should return...)
            dispatchCriteria = mr.getName();
         }

         Response response = buildResponse((RestMockResponse)mr, dispatchCriteria);
         Request request = buildRequest(rtr);
         result.put(request, response);
      }
      return result;
   }

   /**
    * Collect and filter by operation all the WsldTestrequest from the current project.
    */
   private Map<String, WsdlTestRequest> collectWsdlTestRequests(Operation operation){
      Map<String, WsdlTestRequest> result = new HashMap<>();

      for (TestSuite testsuite : project.getTestSuiteList()){
         for (TestCase testcase : testsuite.getTestCaseList()){
            for (TestStep teststep : testcase.getTestStepList()){
               if (teststep instanceof WsdlTestRequestStep){
                  WsdlTestRequestStep ws = (WsdlTestRequestStep)teststep;
                  WsdlTestRequest wr = ws.getHttpRequest();
                  if (wr.getOperationName().equals(operation.getName())){
                     result.put(wr.getName(), wr);
                  }
               }
            }
         }
      }
      return result;
   }

   /**
    * Collect and filter by operation all the WsldTestrequest from the current project.
    */
   private Map<String, RestTestRequest> collectRestTestRequests(Operation operation){
      Map<String, RestTestRequest> result = new HashMap<String, RestTestRequest>();

      for (TestSuite testsuite : project.getTestSuiteList()){
         for (TestCase testcase : testsuite.getTestCaseList()){
            for (TestStep teststep : testcase.getTestStepList()){
               if (teststep instanceof RestTestRequestStep){
                  RestTestRequestStep rs = (RestTestRequestStep)teststep;
                  RestTestRequest rr = rs.getTestRequest();
                  if (rs.getResourcePath().equals(operation.getName())){
                     result.put(rr.getName(), rr);
                  }
               }
            }
         }
      }
      return result;
   }

   /**
    * Build a XPathMatcher based on operation dispatcher rules.
    */
   private XPathExpression initializeXPathMatcher(Operation operation) throws XPathExpressionException {
      return SoapUIXPathBuilder.buildXPathMatcherFromRules(operation.getDispatcherRules());
   }

   /**
    * Build a map where keys are dispatch criteria and values response name.
    */
   private Map<String, String> buildQueryMatchDispatchCriteriaToResponseMap(WsdlMockOperation wmo){
      Map<String, String> matchResponseMap = new HashMap<String, String>();

      //MockOperationDispatcher dispatcher = wmo.getMockOperationDispatcher();
      MockOperationDispatcher dispatcher = wmo.getDispatcher();
      if (dispatcher instanceof QueryMatchMockOperationDispatcher){
         QueryMatchMockOperationDispatcher qmDispatcher = (QueryMatchMockOperationDispatcher) dispatcher;
         for (int i=0; i<qmDispatcher.getQueryCount(); i++){
            QueryMatchMockOperationDispatcher.Query query = qmDispatcher.getQueryAt(i);
            matchResponseMap.put(query.getMatch(), query.getResponse());
         }
      }
      return matchResponseMap;
   }

   /**
    * Build a domain Response from SoapUI MockResponse using this dispatchCriteria.
    */
   private Response buildResponse(MockResponse mockResponse, String dispatchCriteria){
      Response response = new Response();
      response.setName(mockResponse.getName());
      response.setContent(((WsdlMockResponse)mockResponse).getResponseContent());
      response.setHeaders(buildHeaders(((WsdlMockResponse)mockResponse).getResponseHeaders()));
      response.setDispatchCriteria(dispatchCriteria);
      if ("500".equals(((WsdlMockResponse)mockResponse).getConfig().getHttpResponseStatus())) {
         response.setFault(true);
      }
      return response;
   }

   /**
    * Build a domain Response from SoapUI RestMockResponse using this dispatchCriteria.
    */
   private Response buildResponse(RestMockResponse mockResponse, String dispatchCriteria){
      Response response = new Response();
      response.setName(mockResponse.getName());
      response.setContent(mockResponse.getResponseContent());
      response.setHeaders(buildHeaders(mockResponse.getResponseHeaders()));
      response.setStatus(String.valueOf(mockResponse.getResponseHttpStatus()));
      response.setMediaType(mockResponse.getMediaType());
      response.setDispatchCriteria(dispatchCriteria);
      return response;
   }

   /**
    * Build a domain Request from SoapUI WsdlTestRequest.
    */
   private Request buildRequest(WsdlTestRequest wtr){
      Request request = new Request();
      request.setName(wtr.getName());
      request.setContent(wtr.getRequestContent());
      request.setHeaders(buildHeaders(wtr.getRequestHeaders()));
      return request;
   }

   /**
    * Build a domain Request from SoapUI RestTestRequest.
    */
   private Request buildRequest(RestTestRequest rtr){
      Request request = new Request();
      request.setName(rtr.getName());
      request.setContent(rtr.getRequestContent());
      request.setHeaders(buildHeaders(rtr.getRequestHeaders()));
      // Add query parameters only (template are holded by the operation itself.)
      RestParamsPropertyHolder paramsHolder = rtr.getParams();
      for (int i=0; i<paramsHolder.getPropertyCount(); i++){
         //if (paramsHolder.getPropertyAt(i).getStyle() == ParameterStyle.QUERY){
         Parameter param = new Parameter();
         param.setName(paramsHolder.getPropertyAt(i).getName());
         param.setValue(paramsHolder.getPropertyAt(i).getValue());
         request.addQueryParameter(param);
         //}
      }
      return request;
   }

   /**
    * Build a Set of headers from SoapUI header definitions.
    */
   private Set<Header> buildHeaders(StringToStringsMap requestHeaders){
      if (requestHeaders == null || requestHeaders.size() == 0){
         return null;
      }

      // Prepare and map the set of headers.
      Set<Header> headers = new HashSet<>();
      for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()){
         Header header = new Header();
         header.setName(entry.getKey());
         header.setValues(new HashSet<>(entry.getValue()));
         headers.add(header);
      }
      return headers;
   }
}
