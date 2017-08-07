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

import com.github.lbroudoux.microcks.domain.Request;
import com.github.lbroudoux.microcks.domain.Response;
import com.github.lbroudoux.microcks.domain.TestStepResult;

/**
 * A simple bean for wrapping a response code, elapsed time and a response content.
 * @author laurent
 */
public class TestReturn{

   public static final int SUCCESS_CODE = 0;
   public static final int FAILURE_CODE = 1;
   
   private int code;
   private long elapsedTime;
   private String message;
   private Request request;
   private Response response;

   /** Default constructor for enabling bean serialization. */
   public TestReturn() {
   }

   /**
    * Build a TestReturn with its code and response.
    * @param code The code (may be success of failure)
    * @param elapsedTime Time taken for a test
    * @param request The request for this test
    * @param response The response for this test
    */
   public TestReturn(int code, long elapsedTime, Request request, Response response){
      this.code = code;
      this.elapsedTime = elapsedTime;
      this.request = request;
      this.response = response;
   }
   
   /**
    * Build a TestReturn with its code and response.
    * @param code The code (may be success of failure)
    * @param elapsedTime Time taken for a test
    * @param message The return message for this test
    * @param request The request for this test
    * @param response The response for this test
    */
   public TestReturn(int code, long elapsedTime, String message, Request request, Response response){
      this.code = code;
      this.elapsedTime = elapsedTime;
      this.message = message;
      this.request = request;
      this.response = response;
   }
   
   /** @return Return code */
   public int getCode() {
      return code;
   }
   /** @return Elapsed time */
   public long getElapsedTime() {
      return elapsedTime;
   }
   /** @return Test return message */
   public String getMessage() {
      return message;
   }
   /** @return Request content */
   public Request getRequest() {
      return request;
   }
   /** @return Response content */
   public Response getResponse() {
      return response;
   }
   
   /** 
    * Build a TestStepResult from inner elements.
    * @return A new TestStepResult ready to attached to a test case.
    */
   public TestStepResult buildTestStepResult(){
      TestStepResult result = new TestStepResult();
      result.setElapsedTime(elapsedTime);
      result.setRequestName(request.getName());
      result.setSuccess(code == SUCCESS_CODE);
      result.setMessage(message);
      return result;
   }
}
