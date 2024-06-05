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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for class XPathContainsAssertion.
 * @author laurent
 */
class XPathContainsAssertionTest {

   private String validSoap = """
         <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soapenv:Header/>
            <soapenv:Body>
               <hel:sayHelloResponse>
                  <sayHello>Hello Andrew !</sayHello>
               </hel:sayHelloResponse>
            </soapenv:Body>
         </soapenv:Envelope>
         """;

   private String invalidSoap = """
         <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soap:Header/>
            <soap:Body>
               <hel:sayHelloResponse>
                  <sayHello>Andrew!</sayHello>
               </hel:sayHelloResponse>
            </soap:Body>
         </soap:Envelope>
         """;

   @Test
   void testExactMatch() {
      // Passing case.
      Map<String, String> configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "Hello Andrew !");
      SoapUIAssertion assertion = AssertionFactory.intializeAssertion(AssertionFactory.XPATH_CONTAINS, configParams);
      AssertionStatus status = assertion.assertResponse(new RequestResponseExchange(null, null, validSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.VALID, status);

      // Failing case.
      configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "Andrew");
      assertion.configure(configParams);
      status = assertion.assertResponse(new RequestResponseExchange(null, null, validSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.FAILED, status);
   }

   @Test
   void testFuzzyMatch() {
      // Passing case with starting *.
      Map<String, String> configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "*Andrew*", XPathContainsAssertion.ALLOW_WILDCARDS,
            "true");
      SoapUIAssertion assertion = AssertionFactory.intializeAssertion(AssertionFactory.XPATH_CONTAINS, configParams);
      AssertionStatus status = assertion.assertResponse(new RequestResponseExchange(null, null, validSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.VALID, status);

      // Passing case with middle *.
      configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "Hello*!", XPathContainsAssertion.ALLOW_WILDCARDS,
            "true");
      assertion = AssertionFactory.intializeAssertion(AssertionFactory.XPATH_CONTAINS, configParams);
      status = assertion.assertResponse(new RequestResponseExchange(null, null, validSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.VALID, status);

      // Passing case with ending *.
      configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "Hello Andrew*", XPathContainsAssertion.ALLOW_WILDCARDS,
            "true");
      assertion = AssertionFactory.intializeAssertion(AssertionFactory.XPATH_CONTAINS, configParams);
      status = assertion.assertResponse(new RequestResponseExchange(null, null, validSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.VALID, status);

      // Passing case with * everywhere.
      configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "*Hello*Andrew*",
            XPathContainsAssertion.ALLOW_WILDCARDS, "true");
      assertion = AssertionFactory.intializeAssertion(AssertionFactory.XPATH_CONTAINS, configParams);
      status = assertion.assertResponse(new RequestResponseExchange(null, null, validSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.VALID, status);

      // Failing case.
      configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "*Andrew", XPathContainsAssertion.ALLOW_WILDCARDS,
            "true");
      assertion.configure(configParams);
      status = assertion.assertResponse(new RequestResponseExchange(null, null, invalidSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.FAILED, status);

      configParams = Map.of(XPathContainsAssertion.PATH_PARAM, """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """, XPathContainsAssertion.EXPECTED_CONTENT_PARAM, "Andrew*", XPathContainsAssertion.ALLOW_WILDCARDS,
            "true");
      assertion.configure(configParams);
      status = assertion.assertResponse(new RequestResponseExchange(null, null, validSoap, 100L),
            new ExchangeContext(null, null, null, null));
      assertEquals(AssertionStatus.FAILED, status);
   }
}
