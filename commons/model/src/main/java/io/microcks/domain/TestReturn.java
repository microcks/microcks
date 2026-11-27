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
package io.github.microcks.domain;

/**
 * A simple bean for wrapping a test exchange (whether request/response or async event) code, elapsed time and exchange
 * content.
 * @author laurent
 */
public class TestReturn {

   public static final int SUCCESS_CODE = 0;
   public static final int FAILURE_CODE = 1;

   private int code;
   private long elapsedTime;
   private String message;
   private Request request;
   private Response response;
   private EventMessage eventMessage;

   /** Default constructor for enabling bean serialization. */
   public TestReturn() {
   }

   /**
    * Build a TestReturn for event based exchange with its code.
    * @param code         The code (may be success of failure)
    * @param elapsedTime  Time taken for a test
    * @param eventMessage The event message for this test
    */
   public TestReturn(int code, long elapsedTime, EventMessage eventMessage) {
      this.code = code;
      this.elapsedTime = elapsedTime;
      this.eventMessage = eventMessage;
   }

   /**
    * Build a TestReturn for event based exchange with its code.
    * @param code         The code (may be success of failure)
    * @param elapsedTime  Time taken for a test
    * @param message      The return message for this test
    * @param eventMessage The event message for this test
    */
   public TestReturn(int code, long elapsedTime, String message, EventMessage eventMessage) {
      this.code = code;
      this.elapsedTime = elapsedTime;
      this.message = message;
      this.eventMessage = eventMessage;
   }

   /**
    * Build a TestReturn with its code and response.
    * @param code        The code (may be success of failure)
    * @param elapsedTime Time taken for a test
    * @param request     The request for this test
    * @param response    The response for this test
    */
   public TestReturn(int code, long elapsedTime, Request request, Response response) {
      this.code = code;
      this.elapsedTime = elapsedTime;
      this.request = request;
      this.response = response;
   }

   /**
    * Build a TestReturn with its code and response.
    * @param code        The code (may be success of failure)
    * @param elapsedTime Time taken for a test
    * @param message     The return message for this test
    * @param request     The request for this test
    * @param response    The response for this test
    */
   public TestReturn(int code, long elapsedTime, String message, Request request, Response response) {
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

   /** @return EventMessage content */
   public EventMessage getEventMessage() {
      return eventMessage;
   }

   /** @return True is this test return is for a request/response exchange test */
   public boolean isRequestResponseTest() {
      return request != null && response != null;
   }

   /** @return True if this test return is for an asynchronous event test */
   public boolean isEventTest() {
      return eventMessage != null;
   }

   /**
    * Build a TestStepResult from inner elements.
    * @return A new TestStepResult ready to attached to a test case.
    */
   public TestStepResult buildTestStepResult() {
      TestStepResult result = new TestStepResult();
      result.setElapsedTime(elapsedTime);
      result.setSuccess(code == SUCCESS_CODE);
      result.setMessage(message);
      if (request != null) {
         result.setRequestName(request.getName());
      }
      if (eventMessage != null) {
         result.setEventMessageName(eventMessage.getName());
      }
      return result;
   }
}
