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
package io.microcks.test;

import io.microcks.domain.Operation;
import io.microcks.domain.Request;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Service;
import io.microcks.domain.TestReturn;
import io.microcks.util.http.ClientHttpResponse;
import io.microcks.util.http.HttpMethod;
import io.microcks.artifact.soap.SoapMessageValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.List;

/**
 * An extension of HttpTestRunner that checks that returned response is valid according the SOAP contract definition.
 * 
 * @see SoapMessageValidator
 * @author laurent
 */
public class SoapHttpTestRunner extends HttpTestRunner {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapHttpTestRunner.class);

   /** The URL of resources used for validation. */
   private String resourceUrl = null;

   private ResourceReader resourceReader;

   /**
    * Build a runner reading its WSDL from the provided resources.
    * @param resourceReader Access to the resources of a service
    */
   public SoapHttpTestRunner(ResourceReader resourceReader) {
      this.resourceReader = resourceReader;
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

   /**
    * Build the HttpMethod corresponding to string. Always POST for a SoapHttpTestRunner.
    */
   @Override
   public HttpMethod buildMethod(String method) {
      return HttpMethod.POST;
   }

   @Override
   protected int extractTestReturnCode(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse, String responseContent) {
      int code = TestReturn.SUCCESS_CODE;

      // Checking HTTP return code: delegating to super class.
      code = super.extractTestReturnCode(service, operation, request, httpResponse, responseContent);
      // If test is already a failure (40x code), no need to pursue...
      if (TestReturn.FAILURE_CODE == code) {
         return code;
      }

      Resource wsdlResource = resourceReader.findByServiceIdAndType(service.getId(), ResourceType.WSDL).get(0);
      List<String> errors = SoapMessageValidator.validateSoapMessage(wsdlResource.getContent(),
            new QName(service.getXmlNS(), operation.getOutputName()), responseContent, resourceUrl);

      log.debug("SoapBody validation errors: " + errors.size());

      if (!errors.isEmpty()) {
         log.debug("Soap validation errors found " + errors.size() + ", marking test as failed.");
         return TestReturn.FAILURE_CODE;
      }

      return code;
   }
}
