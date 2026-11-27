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

import java.util.Map;

/**
 * Helper class to build and retrieve assertions using the Soap UI type for this assertion.
 * @author laurent
 */
public class AssertionFactory {

   public static final String RESPONSE_SLA_ASSERTION = "Response SLA Assertion";
   public static final String VALID_HTTP_STATUS_CODES = "Valid HTTP Status Codes";
   public static final String SOAP_FAULT_ASSERTION = "Not SOAP Fault Assertion";
   public static final String NOT_SOAP_FAULT_ASSERTION = "SOAP Fault Assertion";
   public static final String SOAP_RESPONSE = "SOAP Response";
   public static final String SCHEMA_COMPLIANCE = "Schema Compliance";
   public static final String XPATH_CONTAINS = "XPath Match";
   public static final String JSONPATH_MATCH = "JsonPath Match";
   public static final String SIMPLE_CONTAINS = "Simple Contains";
   public static final String SIMPLE_NOT_CONTAINS = "Simple NotContains";


   private AssertionFactory() {
      // Hide default constructor as it's a utility class.
   }

   /**
    * Build and configuration a new assertion for the given type.
    * @param type         The type of the assertion (see string constants for available values.)
    * @param configParams The configuration parameters for this assertion
    * @return A ready-to-use assertion
    */
   public static SoapUIAssertion intializeAssertion(String type, Map<String, String> configParams) {
      SoapUIAssertion assertion = null;

      // Depending on type, initialize the correct assertion implementation.
      switch (type) {
         case RESPONSE_SLA_ASSERTION -> assertion = new SLAAssertion();
         case VALID_HTTP_STATUS_CODES -> assertion = new ValidHttpCodesAssertion();
         case SOAP_FAULT_ASSERTION -> assertion = new SoapFaultAssertion();
         case NOT_SOAP_FAULT_ASSERTION -> assertion = new NotSoapFaultAssertion();
         case SOAP_RESPONSE -> assertion = new SoapResponseAssertion();
         case SCHEMA_COMPLIANCE -> assertion = new SchemaConformanceAssertion();
         case XPATH_CONTAINS -> assertion = new XPathContainsAssertion();
         case JSONPATH_MATCH -> assertion = new JsonPathContentAssertion();
         case SIMPLE_CONTAINS -> assertion = new SimpleContainsAssertion();
         case SIMPLE_NOT_CONTAINS -> assertion = new SimpleNotContainsAssertion();
         default -> assertion = new UnknownAssertion(type);
      }

      assertion.configure(configParams);
      return assertion;
   }
}
