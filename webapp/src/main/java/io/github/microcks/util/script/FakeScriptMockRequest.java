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
package io.github.microcks.util.script;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * This is a fake, lightweight implementation of mockRequest objects that are typically available within SoapUI script
 * context (see http://www.soapui.org/Service-Mocking/creating-dynamic-mockservices.html for explanations on how they
 * are used). Such object should follow contract exposed by
 * http://www.soapui.org/apidocs/com/eviware/soapui/impl/wsdl/mock/WsdlMockRequest.html, and because it's Groovy dynamic
 * stuff we do not have to implement a particular interface. Groovy !! isn't it ??
 * @author laurent
 */
public class FakeScriptMockRequest {

   /** The HttpServletRequest wrapped object. */
   private HttpServletRequest request;
   /** The content of mock Request (request body indeed) */
   private String requestContent;
   /** The headers of mock Request (http headers most of the time !) */
   private StringToStringsMap requestHeaders;
   /** The URI parameters of mock Request */
   private Map<String, String> uriParameters;

   /**
    * Create a new fake request from content and headers.
    * @param requestContent The request content
    * @param requestHeaders The request headers
    */
   public FakeScriptMockRequest(String requestContent, StringToStringsMap requestHeaders) {
      this.requestContent = requestContent;
      this.requestHeaders = requestHeaders;
   }

   public HttpServletRequest getRequest() {
      return request;
   }

   public void setRequest(HttpServletRequest request) {
      this.request = request;
   }

   public String getRequestContent() {
      return requestContent;
   }

   public void setRequestContent(String requestContent) {
      this.requestContent = requestContent;
   }

   public StringToStringsMap getRequestHeaders() {
      return requestHeaders;
   }

   public void setRequestHeaders(StringToStringsMap requestHeaders) {
      this.requestHeaders = requestHeaders;
   }

   public void setURIParameters(Map<String, String> uriParameters) {
      this.uriParameters = uriParameters;
   }

   public Map<String, String> getURIParameters() {
      return uriParameters;
   }
}
