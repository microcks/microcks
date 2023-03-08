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
package io.github.microcks.web;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
/**
 * This is a Test for SoapController class.
 * @laurent
 */
public class SoapControllerTest {

   @Test
   public void testOriginalOperationParsing() {
      SoapController soapController = new SoapController();

      String originalPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHello>\n" +
            "         <name>Karla</name>\n" +
            "      </hel:sayHello>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      assertTrue(soapController.hasPayloadCorrectStructureForOperation(originalPayload, "sayHello"));

      assertEquals("sayHello", soapController.extractOperationName(originalPayload));
   }

   @Test
   public void testDashNamespaceOperationParsing() {
      SoapController soapController = new SoapController();

      String dashNamespacePayload = "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n" +
            "   <soap-env:Header/>\n" +
            "   <soap-env:Body>\n" +
            "      <hel:sayHello>\n" +
            "         <name>Karla</name>\n" +
            "      </hel:sayHello>\n" +
            "   </soap-env:Body>\n" +
            "</soap-env:Envelope>";

      assertTrue(soapController.hasPayloadCorrectStructureForOperation(dashNamespacePayload, "sayHello"));

      assertEquals("sayHello", soapController.extractOperationName(dashNamespacePayload));
   }

   @Test
   public void testOriginalOperationWithNSParsing() {
      SoapController soapController = new SoapController();

      String originalPayloadOpWithNamespace = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHello xmlns:hel=\"http://www.example.com/hello\">\n" +
            "         <name>Karla</name>\n" +
            "      </hel:sayHello>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      assertTrue(soapController.hasPayloadCorrectStructureForOperation(originalPayloadOpWithNamespace, "sayHello"));

      assertEquals("sayHello", soapController.extractOperationName(originalPayloadOpWithNamespace));
   }

   @Test
   public void testDashNamespaceOperationWithNSParsing() {
      SoapController soapController = new SoapController();

      String dashNamespacePayloadOpWithNamespace = "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "   <soap-env:Header/>\n" +
            "   <soap-env:Body>\n" +
            "      <hel:sayHello xmlns:hel=\"http://www.example.com/hello\">\n" +
            "         <name>Karla</name>\n" +
            "      </hel:sayHello>\n" +
            "   </soap-env:Body>\n" +
            "</soap-env:Envelope>";

      assertTrue(soapController.hasPayloadCorrectStructureForOperation(dashNamespacePayloadOpWithNamespace, "sayHello"));

      assertEquals("sayHello", soapController.extractOperationName(dashNamespacePayloadOpWithNamespace));
   }

   @Test
   public void testNegativeOperationMatching() {
      SoapController soapController = new SoapController();

      String otherOperationPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHelloWorld xmlns:hel=\"http://www.example.com/hello\">\n" +
            "         <name>Karla</name>\n" +
            "      </hel:sayHelloWorld>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      assertTrue(soapController.hasPayloadCorrectStructureForOperation(otherOperationPayload, "sayHelloWorld"));
      assertFalse(soapController.hasPayloadCorrectStructureForOperation(otherOperationPayload, "sayHello"));

      assertEquals("sayHelloWorld", soapController.extractOperationName(otherOperationPayload));
   }

   @Test
   public void testNoArgOperationMatching() {
      SoapController soapController = new SoapController();

      String noArgOperationPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHelloWorld/>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      String noArgFullOperationPayload = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHelloWorld></hel:sayHelloWorld>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      String noArgOperationPayloadWithNamespace = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHelloWorld xmlns:hel=\"http://www.example.com/hello\"/>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      String noArgFullOperationPayloadWithNamespace = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHelloWorld xmlns:hel=\"http://www.example.com/hello\"></hel:sayHelloWorld>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      assertTrue(soapController.hasPayloadCorrectStructureForOperation(noArgOperationPayload, "sayHelloWorld"));
      assertTrue(soapController.hasPayloadCorrectStructureForOperation(noArgFullOperationPayload, "sayHelloWorld"));
      assertTrue(soapController.hasPayloadCorrectStructureForOperation(noArgOperationPayloadWithNamespace, "sayHelloWorld"));
      assertTrue(soapController.hasPayloadCorrectStructureForOperation(noArgFullOperationPayloadWithNamespace, "sayHelloWorld"));

      assertEquals("sayHelloWorld", soapController.extractOperationName(noArgOperationPayload));
      assertEquals("sayHelloWorld", soapController.extractOperationName(noArgFullOperationPayload));
      assertEquals("sayHelloWorld", soapController.extractOperationName(noArgOperationPayloadWithNamespace));
      assertEquals("sayHelloWorld", soapController.extractOperationName(noArgFullOperationPayloadWithNamespace));
   }

   @Test
   public void testConvertSoapUITemplate() {
      String soapUITemplate = "<something>${myParam}</something>";
      String microcksTemplate = SoapController.convertSoapUITemplate(soapUITemplate);
      assertEquals("<something>{{ myParam }}</something>", microcksTemplate);

      soapUITemplate = "<bean><something>${ myParam}</something><else>${myOtherParam }</else></bean>";
      microcksTemplate = SoapController.convertSoapUITemplate(soapUITemplate);
      assertEquals("<bean><something>{{ myParam }}</something><else>{{ myOtherParam }}</else></bean>", microcksTemplate);

      soapUITemplate = "<bean>\n" +
            "  <something>${myParam}</something>\n" +
            "  <else>${myOtherParam}</else>\n" +
            "</bean>";
      String expectedResult = "<bean>\n" +
            "  <something>{{ myParam }}</something>\n" +
            "  <else>{{ myOtherParam }}</else>\n" +
            "</bean>";
      microcksTemplate = SoapController.convertSoapUITemplate(soapUITemplate);
      assertEquals(expectedResult, microcksTemplate);
   }
}
