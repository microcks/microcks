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
package com.github.lbroudoux.microcks.util.soapui;

import com.eviware.soapui.impl.support.http.HttpRequestTestStep;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.support.http.ProxyUtils;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCaseRunner;
import com.eviware.soapui.impl.wsdl.teststeps.RestRequestStepResult;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStepResult;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestStepResult;
import com.eviware.soapui.model.testsuite.TestStepResult.TestStepStatus;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.support.types.StringToObjectMap;
import com.eviware.soapui.support.types.StringToStringsMap;
import com.github.lbroudoux.microcks.domain.*;
import com.github.lbroudoux.microcks.util.test.AbstractTestRunner;
import com.github.lbroudoux.microcks.util.test.TestReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This is a utility class for running Service tests using assertions defined under a corresponding SoapUI
 * project. Simply build a new SoapUITestStepsRunner referencing the local path to project file and
 * call <i>runAllTestSteps()</i> or <i>runTestSteps()</i> to pick only some of them.<br/> 
 * For now, this class only runs SoapUI test steps corresponding to Request Test Steps (either SOAP, REST or HTTP).
 * @author laurent
 */
public class SoapUITestStepsRunner extends AbstractTestRunner<HttpMethod> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapUITestStepsRunner.class);
   
   private WsdlProject project;
   
   /**
    * Build a new SoapUITestStepsRunner for a project.
    * @param projectFilePath The path to SoapUI project file
    * @throws java.io.IOException if file cannot be found or accessed.
    */
   public SoapUITestStepsRunner(String projectFilePath) throws IOException{
      try{
         project = new WsdlProject(projectFilePath);
      } catch (Exception e) {
         throw new IOException("SoapUI project file cannot be found or accessed");
      }
   }

   @Override
   public List<TestReturn> runTest(Service service, Operation operation, List<Request> requests, String endpointUrl, HttpMethod method) throws URISyntaxException, IOException {
      return runAllTestSteps(endpointUrl);
   }

   @Override
   public HttpMethod buildMethod(String method) {
      return null;
   }

   /**
    * Run all the test steps defined into the SoapUI project.
    * @param endpointUrl The URL of the endpoint to use for request test steps.
    * @return A list of TestReturn wrapper objects (one by executed test step)
    */
   public List<TestReturn> runAllTestSteps(String endpointUrl){
      return runTestSteps(endpointUrl, null);
   }
   
   /**
    * Run the test step defined into the SoapUI project and having the name 
    * contained into testStepNames.
    * @param endpointUrl The URL of the endpoint to use for request test steps.
    * @param testStepNames A list of test step names to execute
    * @return A list of TestReturn wrapper objects (one by executed test step)
    */
   public List<TestReturn> runTestSteps(String endpointUrl, List<String> testStepNames){
      // Remember to force no proxy otherwise SoapUI will use system settings and will 
      // make them generally applied to everything going out through Apache Http Client
      // (and maybe also JDK HttpURLConnection ?).
      ProxyUtils.setProxyEnabled(false);
      
      List<TestReturn> results = new ArrayList<TestReturn>();
      
      for (TestSuite testSuite : project.getTestSuiteList()){
         for (TestCase testCase : testSuite.getTestCaseList()){
            // Depending on testCase type build an accurate runner.
            TestCaseRunner testCaseRunner = buildTestCaseRunner(testCase);
            
            if (testCaseRunner != null){
               for (TestStep testStep : testCase.getTestStepList()){
                  if (testStep instanceof HttpRequestTestStep && 
                        (testStepNames == null || testStepNames.contains(testStep.getName()))){
                     
                     log.debug("Picking up step " + testStep.getName() + " for running SoapUI test");
                     // Set the endpointUrl using this common interface for Soap and Rest requests.
                     ((HttpRequestTestStep)testStep).getHttpRequest().setEndpoint(endpointUrl);
                     
                     // Running tests also checks linked assertions.
                     TestStepResult result = testStep.run(testCaseRunner, testCaseRunner.getRunContext());
                     log.debug("SoapUI test result is " + result.getStatus());
                     
                     results.add(extractTestReturn(testStep.getName(), result));
                  }
               }
            }
         }
      }
      return results;
   }
   
   /** */
   private TestCaseRunner buildTestCaseRunner(TestCase testCase){
      if (testCase instanceof WsdlTestCase){
         return new WsdlTestCaseRunner((WsdlTestCase)testCase, new StringToObjectMap());
      }
      return null;
   }
   
   /** */
   private TestReturn extractTestReturn(String testStepName, TestStepResult result){
      int code = TestReturn.FAILURE_CODE;
      if (result.getStatus() == TestStepStatus.OK){
         code = TestReturn.SUCCESS_CODE;
      } 
      String message = null;
      
      // Re-build request and response.
      Request request = new Request();
      request.setName(testStepName);
      Response response = new Response();
      
      // SoapUI step result class do not implement a common interface...
      if (result instanceof WsdlTestRequestStepResult){
         WsdlTestRequestStepResult wtrsr = (WsdlTestRequestStepResult)result;
         request.setContent(wtrsr.getRequestContent());
         request.setHeaders(buildHeaders(wtrsr.getRequestHeaders()));
         response.setContent(wtrsr.getResponseContent());
         response.setHeaders(buildHeaders(wtrsr.getResponseHeaders()));
         message = buildConsolidatedMessage(wtrsr.getMessages());
      }
      if (result instanceof RestRequestStepResult){
         RestRequestStepResult rrsr = (RestRequestStepResult)result;
         request.setContent(rrsr.getRequestContent());
         request.setHeaders(buildHeaders(rrsr.getRequestHeaders()));
         response.setContent(rrsr.getResponseContent());
         response.setHeaders(buildHeaders(rrsr.getResponseHeaders()));
         message = buildConsolidatedMessage(rrsr.getMessages());
         // Status may also be unknown if no assertion is present within a Rest request
         // test step (see https://code.google.com/p/soap-ui-haufe/source/browse/branches/vit/src/java/com/eviware/soapui/impl/wsdl/teststeps/RestTestRequestStep.java?r=19#893)
         // or if endpoint is not reached. Consider 404 as a failure in our case.
         if (result.getStatus() == TestStepStatus.UNKNOWN){
            if (rrsr.getResponse().getStatusCode() == 404){
               code = TestReturn.FAILURE_CODE;
            } else {
               code = TestReturn.SUCCESS_CODE;
            }
         }
      }
      return new TestReturn(code, result.getTimeTaken(), message, request, response);
   }
   
   /** */
   private Set<Header> buildHeaders(StringToStringsMap headers){
      if (headers != null && headers.size() > 0){
         Set<Header> results = new HashSet<>();
         for (Entry<String, List<String>> entry : headers.entrySet()){
            Header header = new Header();
            header.setName(entry.getKey());
            header.setValues(new HashSet<>(entry.getValue()));
            results.add(header);
         }
         return results;
      }
      return null;
   }
   
   /** */
   private String buildConsolidatedMessage(String[] messages){
      if (messages == null){
         return null;
      }
      // Else build a consolidation by adding line delimiters.
      StringBuilder result = new StringBuilder();
      for (String message : messages){
         result.append(message);
         result.append("<br/>").append("================").append("<br/>");
      }
      return result.toString();
   }
}
