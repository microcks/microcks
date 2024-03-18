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
package io.github.microcks.util.soapui.assertions;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.XmlSchemaValidator;
import io.github.microcks.util.soap.SoapMessageValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Assertion that checks if response content is actually conformance to a Schema definition.
 * @author laurent
 */
public class SchemaConformanceAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SchemaConformanceAssertion.class);

   /** The definition parameter to embed a schema definition as a string. */
   public static final String DEFINITION_PARAM = "definition";

   private String definition;

   private List<String> errorMessages;

   @Override
   public void configure(Map<String, String> configParams) {
      if (configParams.containsKey(DEFINITION_PARAM)) {
         definition = configParams.get(DEFINITION_PARAM);
      }
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {

      if (context.service().getType() == ServiceType.SOAP_HTTP) {
         log.debug("Asserting Soap response is valid against WSDL");

         // Validate against Soap message against WSDL.
         Resource wsdl = context.resources().stream().filter(r -> r.getType() == ResourceType.WSDL).findFirst().get();
         QName partQName = new QName(context.service().getXmlNS(), context.operation().getOutputName());
         errorMessages = SoapMessageValidator.validateSoapMessage(wsdl.getContent(), partQName,
               exchange.responseContent(), context.resourceUrl());
         if (!errorMessages.isEmpty()) {
            log.debug("Soap response is not valid: {} errors", errorMessages.size());
            return AssertionStatus.FAILED;
         }
      } else if (definition != null) {
         // Only managed Xml validation at the moment.
         if (exchange.response().getHeaders().getConnection() != null
               && exchange.response().getHeaders().getConnection().contains("xml")) {
            log.debug("Asserting Xml response is valid against local definition");

            try {
               errorMessages = XmlSchemaValidator.validateXml(
                     new ByteArrayInputStream(definition.getBytes(StandardCharsets.UTF_8)), exchange.responseContent(),
                     context.resourceUrl());
            } catch (Exception e) {
               log.warn("Xml schema validation failed with: {}", e.getMessage());
               errorMessages.add("Xml schema validation failed with: " + e.getMessage());
            }
            if (!errorMessages.isEmpty()) {
               log.debug("Xml response is not valid according definition: {} errors", errorMessages.size());
               return AssertionStatus.FAILED;
            }
         }
      }
      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return errorMessages;
   }
}
