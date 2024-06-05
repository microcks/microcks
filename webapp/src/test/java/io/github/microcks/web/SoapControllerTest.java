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
package io.github.microcks.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a Test for SoapController class.
 * @laurent
 */
class SoapControllerTest {

   @Test
   void testOriginalOperationParsing() {
      String originalPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHello>\n"
            + "         <name>Karla</name>\n" + "      </hel:sayHello>\n" + "   </soapenv:Body>\n"
            + "</soapenv:Envelope>";

      assertTrue(SoapController.hasPayloadCorrectStructureForOperation(originalPayload, "sayHello"));

      assertEquals("sayHello", SoapController.extractOperationName(originalPayload));
   }

   @Test
   void testDashNamespaceOperationParsing() {
      String dashNamespacePayload = "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soap-env:Header/>\n" + "   <soap-env:Body>\n" + "      <hel:sayHello>\n"
            + "         <name>Karla</name>\n" + "      </hel:sayHello>\n" + "   </soap-env:Body>\n"
            + "</soap-env:Envelope>";

      assertTrue(SoapController.hasPayloadCorrectStructureForOperation(dashNamespacePayload, "sayHello"));

      assertEquals("sayHello", SoapController.extractOperationName(dashNamespacePayload));
   }

   @Test
   void testOriginalOperationWithNSParsing() {
      String originalPayloadOpWithNamespace = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n"
            + "      <hel:sayHello xmlns:hel=\"http://www.example.com/hello\">\n" + "         <name>Karla</name>\n"
            + "      </hel:sayHello>\n" + "   </soapenv:Body>\n" + "</soapenv:Envelope>";

      assertTrue(SoapController.hasPayloadCorrectStructureForOperation(originalPayloadOpWithNamespace, "sayHello"));

      assertEquals("sayHello", SoapController.extractOperationName(originalPayloadOpWithNamespace));
   }

   @Test
   void testDashNamespaceOperationWithNSParsing() {
      String dashNamespacePayloadOpWithNamespace = "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "   <soap-env:Header/>\n" + "   <soap-env:Body>\n"
            + "      <hel:sayHello xmlns:hel=\"http://www.example.com/hello\">\n" + "         <name>Karla</name>\n"
            + "      </hel:sayHello>\n" + "   </soap-env:Body>\n" + "</soap-env:Envelope>";

      assertTrue(
            SoapController.hasPayloadCorrectStructureForOperation(dashNamespacePayloadOpWithNamespace, "sayHello"));

      assertEquals("sayHello", SoapController.extractOperationName(dashNamespacePayloadOpWithNamespace));
   }

   @Test
   void testNegativeOperationMatching() {
      String otherOperationPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n"
            + "      <hel:sayHelloWorld xmlns:hel=\"http://www.example.com/hello\">\n" + "         <name>Karla</name>\n"
            + "      </hel:sayHelloWorld>\n" + "   </soapenv:Body>\n" + "</soapenv:Envelope>";

      assertTrue(SoapController.hasPayloadCorrectStructureForOperation(otherOperationPayload, "sayHelloWorld"));
      assertFalse(SoapController.hasPayloadCorrectStructureForOperation(otherOperationPayload, "sayHello"));

      assertEquals("sayHelloWorld", SoapController.extractOperationName(otherOperationPayload));
   }

   @Test
   void testNoArgOperationMatching() {
      String noArgOperationPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHelloWorld/>\n" + "   </soapenv:Body>\n"
            + "</soapenv:Envelope>";

      String noArgFullOperationPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHelloWorld></hel:sayHelloWorld>\n"
            + "   </soapenv:Body>\n" + "</soapenv:Envelope>";

      String noArgOperationPayloadWithNamespace = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n"
            + "      <hel:sayHelloWorld xmlns:hel=\"http://www.example.com/hello\"/>\n" + "   </soapenv:Body>\n"
            + "</soapenv:Envelope>";

      String noArgFullOperationPayloadWithNamespace = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n"
            + "      <hel:sayHelloWorld xmlns:hel=\"http://www.example.com/hello\"></hel:sayHelloWorld>\n"
            + "   </soapenv:Body>\n" + "</soapenv:Envelope>";

      assertTrue(SoapController.hasPayloadCorrectStructureForOperation(noArgOperationPayload, "sayHelloWorld"));
      assertTrue(SoapController.hasPayloadCorrectStructureForOperation(noArgFullOperationPayload, "sayHelloWorld"));
      assertTrue(
            SoapController.hasPayloadCorrectStructureForOperation(noArgOperationPayloadWithNamespace, "sayHelloWorld"));
      assertTrue(SoapController.hasPayloadCorrectStructureForOperation(noArgFullOperationPayloadWithNamespace,
            "sayHelloWorld"));

      assertEquals("sayHelloWorld", SoapController.extractOperationName(noArgOperationPayload));
      assertEquals("sayHelloWorld", SoapController.extractOperationName(noArgFullOperationPayload));
      assertEquals("sayHelloWorld", SoapController.extractOperationName(noArgOperationPayloadWithNamespace));
      assertEquals("sayHelloWorld", SoapController.extractOperationName(noArgFullOperationPayloadWithNamespace));
   }

   @Test
   void testConvertSoapUITemplate() {
      String soapUITemplate = "<something>${myParam}</something>";
      String microcksTemplate = SoapController.convertSoapUITemplate(soapUITemplate);
      assertEquals("<something>{{ myParam }}</something>", microcksTemplate);

      soapUITemplate = "<bean><something>${ myParam}</something><else>${myOtherParam }</else></bean>";
      microcksTemplate = SoapController.convertSoapUITemplate(soapUITemplate);
      assertEquals("<bean><something>{{ myParam }}</something><else>{{ myOtherParam }}</else></bean>",
            microcksTemplate);

      soapUITemplate = "<bean>\n" + "  <something>${myParam}</something>\n" + "  <else>${myOtherParam}</else>\n"
            + "</bean>";
      String expectedResult = "<bean>\n" + "  <something>{{ myParam }}</something>\n"
            + "  <else>{{ myOtherParam }}</else>\n" + "</bean>";
      microcksTemplate = SoapController.convertSoapUITemplate(soapUITemplate);
      assertEquals(expectedResult, microcksTemplate);
   }
}
