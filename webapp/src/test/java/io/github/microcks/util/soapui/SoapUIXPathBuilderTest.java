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
package io.github.microcks.util.soapui;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathExpression;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class SoapUIXPathBuilder class.
 * @author laurent
 */
class SoapUIXPathBuilderTest {

   @Test
   void testBuildXPathMatcherFromRulesSimple() {

      String rules = "declare namespace ser='http://www.example.com/hello';\n" + "//ser:sayHello/name";
      String soap = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHello>\n"
            + "         <name>Karla</name>\n" + "      </hel:sayHello>\n" + "   </soapenv:Body>\n"
            + "</soapenv:Envelope>";

      XPathExpression expression = null;

      try {
         expression = SoapUIXPathBuilder.buildXPathMatcherFromRules(rules);
      } catch (Throwable t) {
         fail("No exception should be thrown while parsing rules");
      }

      String result = null;
      try {
         result = expression.evaluate(new InputSource(new StringReader(soap)));
      } catch (Throwable t) {
         fail("No exception should be thrown while evaluating xpath");
      }
      assertEquals("Karla", result);
   }

   @Test
   void testBuildXPathMatcherFromRulesFunction() {

      String rules = "declare namespace ser='http://www.example.com/hello';\n"
            + "concat(//ser:sayHello/title/text(),' ',//ser:sayHello/name/text())";
      String soap = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHello>\n"
            + "         <title>Ms.</title>\n" + "         <name>Karla</name>\n" + "      </hel:sayHello>\n"
            + "   </soapenv:Body>\n" + "</soapenv:Envelope>";

      XPathExpression expression = null;

      try {
         expression = SoapUIXPathBuilder.buildXPathMatcherFromRules(rules);
      } catch (Throwable t) {
         fail("No exception should be thrown while parsing rules");
      }

      String result = null;
      try {
         result = expression.evaluate(new InputSource(new StringReader(soap)));
      } catch (Throwable t) {
         fail("No exception should be thrown while evaluating xpath");
      }
      assertEquals("Ms. Karla", result);
   }
}
