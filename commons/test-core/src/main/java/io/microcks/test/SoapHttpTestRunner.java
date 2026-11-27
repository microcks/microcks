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
package io.github.microcks.util.test;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.util.soap.SoapMessageValidator;
/*
import io.github.microcks.util.SoapMessageValidator;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.util.UriUtils;

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

   private ResourceRepository resourceRepository;

   /**
    *
    * @param resourceRepository
    */
   public SoapHttpTestRunner(ResourceRepository resourceRepository) {
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

      Resource wsdlResource = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.WSDL).get(0);
      List<String> errors = SoapMessageValidator.validateSoapMessage(wsdlResource.getContent(),
            new QName(service.getXmlNS(), operation.getOutputName()), responseContent, resourceUrl);

      log.debug("SoapBody validation errors: " + errors.size());

      if (!errors.isEmpty()) {
         log.debug("Soap validation errors found " + errors.size() + ", marking test as failed.");
         return TestReturn.FAILURE_CODE;
      }

      /*
       * try{ // Validate Soap message body according to operation output part. List<XmlError> errors =
       * SoapMessageValidator.validateSoapMessage( operation.getOutputName(), service.getXmlNS(), responseContent,
       * resourceUrl + UriUtils.encodeFragment(service.getName(), "UTF-8") + "-" + service.getVersion() + ".wsdl", true
       * );
       * 
       * if (!errors.isEmpty()){ log.debug("Soap validation errors found " + errors.size() +
       * ", marking test as failed."); return TestReturn.FAILURE_CODE; } } catch (XmlException e) {
       * log.debug("XmlException while validating Soap response message", e); return TestReturn.FAILURE_CODE; }
       */
      return code;
   }
}
