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
package io.github.microcks.util.soap;

import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for SoapMessageValidator class.
 * @author laurent
 */
class SoapMessageValidatorTest {

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

   private String validSoapRequest = """
         <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soapenv:Header/>
            <soapenv:Body>
               <hel:sayHello>
                  <name>Andrew</name>
               </hel:sayHello>
            </soapenv:Body>
         </soapenv:Envelope>
         """;

   private String validSoapNSOnBody = """
         <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
            <soapenv:Header/>
            <soapenv:Body xmlns:hel="http://www.example.com/hello">
               <hel:sayHelloResponse>
                  <sayHello>Hello Andrew !</sayHello>
               </hel:sayHelloResponse>
            </soapenv:Body>
         </soapenv:Envelope>
         """;

   private String invalidSoap = """
         <soap:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soap:Header/>
            <soap:Body>
               <hel:sayHelloResponse>
                  <sayHello>Hello Andrew !</sayHello>
               </hel:sayHelloResponse>
            </soap:Body>
         </soap:Envelope>
         """;

   private String invalidSoapMessage = """
         <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soap:Header/>
            <soap:Body>
               <hel:sayHelloResponse>
                  <sayHello>Hello Andrew !</sayHello>
                  <foo>Bar</foo>
               </hel:sayHelloResponse>
            </soap:Body>
         </soap:Envelope>
         """;

   private String validSoap12 = """
         <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:hel="http://www.example.com/hello">
            <soap:Header/>
            <soap:Body>
               <hel:sayHelloResponse>
                  <sayHello>Hello Andrew !</sayHello>
               </hel:sayHelloResponse>
            </soap:Body>
         </soap:Envelope>
         """;

   private String invalidSoap12 = """
         <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:hel="http://www.example.com/hello">
            <soap:Header/>
            <soap:Body>
               <hel:sayHelloResponse>
                  <sayHello>Hello Andrew !</sayHello>
               </hel:sayHelloResponse>
            </soap:Body>
            <soap:Footer>
               <foo>bar</foo>
            </soap:Footer>
         </soap:Envelope>
         """;

   @Test
   void testValidateSoapEnvelope() {
      List<String> errors = SoapMessageValidator.validateSoapEnvelope(validSoap);
      assertTrue(errors.isEmpty());

      errors = SoapMessageValidator.validateSoapEnvelope(invalidSoap);
      assertFalse(errors.isEmpty());
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).contains("soap:Envelope"));

      errors = SoapMessageValidator.validateSoapEnvelope(validSoap12);
      assertTrue(errors.isEmpty());

      errors = SoapMessageValidator.validateSoapEnvelope(invalidSoap12);
      assertFalse(errors.isEmpty());
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).contains("soap:Footer"));
   }

   @Test
   void testValidateSoapMessage() {
      String wsdlContent = null;
      try {
         wsdlContent = new String(
               Files.readAllBytes(Paths.get("target/test-classes/io/github/microcks/util/soap/HelloService.wsdl")),
               StandardCharsets.UTF_8);
      } catch (Exception e) {
         fail("No exception should not occur here");
      }

      List<String> errors = SoapMessageValidator.validateSoapMessage(wsdlContent,
            new QName("http://www.example.com/hello", "sayHelloResponse"), validSoap, "http://localhost:8080");
      assertTrue(errors.isEmpty());

      errors = SoapMessageValidator.validateSoapMessage(wsdlContent,
            new QName("http://www.example.com/hello", "sayHelloResponse"), validSoapNSOnBody, "http://localhost:8080");
      assertTrue(errors.isEmpty());

      errors = SoapMessageValidator.validateSoapMessage(wsdlContent,
            new QName("http://www.example.com/hello", "sayHelloResponse"), validSoapRequest, "http://localhost:8080");
      assertFalse(errors.isEmpty());
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).contains("Expecting a {http://www.example.com/hello}sayHelloResponse element"));

      errors = SoapMessageValidator.validateSoapMessage(wsdlContent,
            new QName("http://www.example.com/hello", "sayHelloResponse"), invalidSoapMessage, "http://localhost:8080");
      assertFalse(errors.isEmpty());
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).contains("foo"));
   }

   @Test
   void testValidateSoapMessageWithDistributedNS() {
      String soapMessage = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
               <soapenv:Header/>
               <soapenv:Body>
                  <hel:sayHello xmlns:hel="http://www.example.com/hello">
                     <name>Andrew</name>
                  </hel:sayHello>
               </soapenv:Body>
            </soapenv:Envelope>
            """;

      String wsdlContent = null;
      try {
         wsdlContent = new String(
               Files.readAllBytes(Paths.get("target/test-classes/io/github/microcks/util/soap/HelloService.wsdl")),
               StandardCharsets.UTF_8);
      } catch (Exception e) {
         fail("No exception should not occur here");
      }

      List<String> errors = SoapMessageValidator.validateSoapMessage(wsdlContent,
            new QName("http://www.example.com/hello", "sayHello"), soapMessage, "http://localhost:8080");
      assertTrue(errors.isEmpty());
   }
}
