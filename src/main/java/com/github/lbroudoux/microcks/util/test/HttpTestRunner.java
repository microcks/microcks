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
package com.github.lbroudoux.microcks.util.test;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.github.lbroudoux.microcks.domain.*;
import com.github.lbroudoux.microcks.util.URIBuilder;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

/**
 * An extension of AbstractTestRunner that checks that returned response is
 * valid according the HTTP code of the response (OK if code in the 20x range, KO in
 * the 30x and over range).
 * @author laurent
 */
public class HttpTestRunner extends AbstractTestRunner<HttpMethod>{

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(HttpTestRunner.class);
         
   private ClientHttpRequestFactory clientHttpRequestFactory;

   /**
    * Get the ClientHttpRequestFactory used for reaching endpoint. 
    * @return The ClientHttpRequestFactory used for reaching endpoint
    */
   public ClientHttpRequestFactory getClientHttpRequestFactory() {
      return clientHttpRequestFactory;
   }
   
   /** 
    * Set the ClientHttpRequestFactory used for reaching endpoint.
    * @param clientHttpRequestFactory The ClientHttpRequestFactory used for reaching endpoint
    */
   public void setClientHttpRequestFactory( ClientHttpRequestFactory clientHttpRequestFactory) {
      this.clientHttpRequestFactory = clientHttpRequestFactory;
   }
   
   @Override
   public List<TestReturn> runTest(Service service, Operation operation, TestResult testResult,
                                   List<Request> requests, String endpointUrl, HttpMethod method) throws URISyntaxException, IOException{
      if (log. isDebugEnabled()){
         log.debug("Launching test run on " + endpointUrl + " for " + requests.size() + " request(s)");
      }
      
      // Initialize result container.
      List<TestReturn> result = new ArrayList<TestReturn>();
      
      for (Request request : requests){
         // Reset status code, message and request each time.
         int code = TestReturn.SUCCESS_CODE;
         String message = null;
         String customizedEndpointUrl = endpointUrl;
         if (service.getType().equals(ServiceType.REST)){
            customizedEndpointUrl += URIBuilder.buildURIFromPattern(operation.getName(), request.getQueryParameters());
            log.debug("Using customized endpoint url: " + customizedEndpointUrl);
         }
         ClientHttpRequest httpRequest = clientHttpRequestFactory.createRequest(new URI(customizedEndpointUrl), method);
         
         // Set headers to request if any.
         Set<Header> headers = request.getHeaders();
         if (headers != null && headers.size() > 0){
            for (Header header : headers){
               httpRequest.getHeaders().add(header.getName(), buildValue(header.getValues()));
            }
         }
         httpRequest.getBody().write(request.getContent().getBytes());
         
         // Actually execute request.
         long startTime = System.currentTimeMillis();
         ClientHttpResponse httpResponse = null;
         try{
            httpResponse = httpRequest.execute();
         } catch (IOException ioe){
            log.error("IOException while executing request " + request.getName() + " on " + endpointUrl, ioe);
            code = TestReturn.FAILURE_CODE;
            message = ioe.getMessage();
         }
         long duration = System.currentTimeMillis() - startTime;
         
         // If still in success, check if http code is out of correct ranges (20x and 30x).
         if (code == TestReturn.SUCCESS_CODE){
            code = extractTestReturnCode(service, operation, request, httpResponse);
            message = extractTestReturnMessage(service, operation, request, httpResponse);
         }
         
         // Create a Response object for returning.
         Response response = new Response();
         
         // Extract response body and headers if there's one.
         if (httpResponse != null){
            StringWriter writer = new StringWriter();
            IOUtils.copy(httpResponse.getBody(), writer);
            response.setContent(writer.toString());
            
            headers = buildHeaders(httpResponse);
            if (headers != null){
               response.setHeaders(headers);
            }
         }
         
         result.add(new TestReturn(code, duration, message, request, response));
      }
      return result;
   }
   
   /**
    * Build the HttpMethod corresponding to string. Default to POST
    * if unknown or unrecognized.
    */
   @Override
   public HttpMethod buildMethod(String method){
      if (method != null){
         if ("GET".equals(method.toUpperCase().trim())){
            return HttpMethod.GET;
         } else if ("PUT".equals(method.toUpperCase().trim())){
            return HttpMethod.PUT;
         } else if ("DELETE".equals(method.toUpperCase().trim())){
            return HttpMethod.DELETE;
         }
      }
      return HttpMethod.POST;
   }

   /**
    * This is a hook for allowing sub-classes to redefine the criteria for
    * telling a response is a success or a failure. This implementation check raw http code
    * (success if in 20x range, failure if not).
    * @param service The service under test
    * @param operation The tested operation
    * @param request The tested reference request
    * @param httpResponse The received response from endpoint
    * @return The test result code, wether TestReturn.SUCCESS_CODE or TestReturn.FAILURE_CODE 
    */
   protected int extractTestReturnCode(Service service, Operation operation, Request request, ClientHttpResponse httpResponse){
      int code = TestReturn.SUCCESS_CODE;
      // Set code to failure if http code out of correct ranges (20x and 30x).
      try{
         if (httpResponse.getRawStatusCode() > 299){
            log.debug("Http status code is " + httpResponse.getRawStatusCode() + ", marking test as failed.");
            code = TestReturn.FAILURE_CODE;
         }
      } catch (IOException ioe){
         log.debug("IOException while getting raw status code in response", ioe);
         code = TestReturn.FAILURE_CODE;
      }
      return code;
   }
   
   /**
    * This is a hook for allowing sub-classes to redefine the extraction of success or failure message.
    * This implementation just extract raw http code.
    * @param service The service under test
    * @param operation The tested operation
    * @param request The tested reference request
    * @param httpResponse The received response from endpoint
    * @return The test result message. 
    */
   protected String extractTestReturnMessage(Service service, Operation operation, Request request, ClientHttpResponse httpResponse){
      String message = null;
      // Set code to failure if http code out of correct ranges (20x and 30x).
      try{
         message = String.valueOf(httpResponse.getRawStatusCode());
      } catch (IOException ioe){
         log.debug("IOException while getting raw status code in response", ioe);
         message = "IOException while getting raw status code in response";
      }
      return message;
   }
   
   /** Build domain headers from ClientHttpResponse ones. */
   private Set<Header> buildHeaders(ClientHttpResponse httpResponse){
      if (httpResponse.getHeaders() != null && !httpResponse.getHeaders().isEmpty()){
         Set<Header> headers = new HashSet<>();
         HttpHeaders responseHeaders = httpResponse.getHeaders();
         for (Entry<String, List<String>> responseHeader : responseHeaders.entrySet()){
            Header header = new Header();
            header.setName(responseHeader.getKey());
            header.setValues(new HashSet<>(responseHeader.getValue()));
            headers.add(header);
         }
         return headers;
      }
      return null;
   }
}
