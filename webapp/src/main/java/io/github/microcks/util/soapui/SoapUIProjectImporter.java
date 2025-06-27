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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.util.MockRepositoryImportException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class SoapUIProjectImporter.
 * @author laurent
 */
class SoapUIProjectImporterTest {

   @Test
   void testSimpleProjectImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/RefTest-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("HelloServiceSoapBinding", service.getName());
      assertEquals("http://lbroudoux.github.com/test/service", service.getXmlNS());
      assertEquals("1.2", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().iterator().next();
      assertEquals("sayHello", operation.getName());
      assertEquals("sayHello", operation.getInputName());
      assertEquals("sayHelloResponse", operation.getOutputName());

      // Check mock dispatching rules.
      assertEquals("QUERY_MATCH", operation.getDispatcher());
      assertTrue(operation.getDispatcherRules()
            .contains("declare namespace ser='http://lbroudoux.github.com/test/service';"));
      assertTrue(operation.getDispatcherRules().contains("//ser:sayHello/name"));

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());

      Resource resource = resources.get(0);
      Assertions.assertEquals(ResourceType.SOAP_UI_PROJECT, resource.getType());
      assertEquals("HelloServiceSoapBinding-1.2.xml", resource.getName());
      assertNotNull(resource.getContent());

      resource = resources.get(1);
      Assertions.assertEquals(ResourceType.WSDL, resource.getType());
      assertEquals("HelloServiceSoapBinding-1.2.wsdl", resource.getName());
      assertNotNull(resource.getContent());

      // Check that messages have been correctly found.
      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (Exception e) {
         e.printStackTrace();
         fail("No exception should be thrown when importing message definitions.");
      }
      assertEquals(2, exchanges.size());
      for (Exchange exchange : exchanges) {
         if (exchange instanceof RequestResponsePair) {
            RequestResponsePair entry = (RequestResponsePair) exchange;
            Request request = entry.getRequest();
            Response response = entry.getResponse();
            assertNotNull(request);
            assertNotNull(response);
            if ("Anne Request".equals(request.getName())) {
               assertEquals(3, request.getHeaders().size());
               assertEquals("Anne Response", response.getName());
               assertEquals("Anne", response.getDispatchCriteria());
            } else if ("Laurent Request".equals(request.getName())) {
               assertEquals("Laurent Response", response.getName());
               assertEquals("Laurent", response.getDispatchCriteria());
            }
         } else {
            fail("Exchange has the wrong type. Expecting RequestResponsePair");
         }
      }
   }

   @Test
   void testSimpleScriptWithSOAPFaultProjectImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/HelloService-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("HelloService Mock", service.getName());
      assertEquals("http://www.example.com/hello", service.getXmlNS());
      assertEquals("0.9", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().iterator().next();
      assertEquals("sayHello", operation.getName());
      assertEquals("sayHello", operation.getInputName());
      assertEquals("sayHelloResponse", operation.getOutputName());

      // Check mock dispatching rules.
      assertEquals("SCRIPT", operation.getDispatcher());
      assertTrue(operation.getDispatcherRules().contains("import com.eviware.soapui.support.XmlHolder"));

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());

      Resource resource = resources.get(0);
      assertEquals(ResourceType.SOAP_UI_PROJECT, resource.getType());
      assertEquals("HelloService Mock-0.9.xml", resource.getName());
      assertNotNull(resource.getContent());

      resource = resources.get(1);
      assertEquals(ResourceType.WSDL, resource.getType());
      assertEquals("HelloService Mock-0.9.wsdl", resource.getName());
      assertNotNull(resource.getContent());

      // Check that messages have been correctly found.
      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (Exception e) {
         fail("No exception should be thrown when importing message definitions.");
      }
      assertEquals(3, exchanges.size());
      for (Exchange exchange : exchanges) {
         if (exchange instanceof RequestResponsePair) {
            RequestResponsePair entry = (RequestResponsePair) exchange;
            Request request = entry.getRequest();
            Response response = entry.getResponse();
            assertNotNull(request);
            assertNotNull(response);
            if ("Andrew Request".equals(request.getName())) {
               assertEquals("Andrew Response", response.getName());
               assertEquals("Andrew Response", response.getDispatchCriteria());
            } else if ("Karla Request".equals(request.getName())) {
               assertEquals("Karla Response", response.getName());
               assertEquals("Karla Response", response.getDispatchCriteria());
            } else if ("World Request".equals(request.getName())) {
               assertEquals("World Response", response.getName());
               assertEquals("World Response", response.getDispatchCriteria());
               assertTrue(response.isFault());
            }
         } else {
            fail("Exchange has the wrong type. Expecting RequestResponsePair");
         }
      }
   }

   @Test
   void testSimpleScriptProjectImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/RefTest-script-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("HelloServiceScriptBinding", service.getName());
      assertEquals("http://lbroudoux.github.com/test/service", service.getXmlNS());
      assertEquals("1.0", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().iterator().next();
      assertEquals("sayHello", operation.getName());
      assertEquals("sayHello", operation.getInputName());
      assertEquals("sayHelloResponse", operation.getOutputName());

      // Check mock dispatching rules.
      assertEquals("SCRIPT", operation.getDispatcher());
      assertTrue(operation.getDispatcherRules().contains("import com.eviware.soapui.support.XmlHolder"));

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());
      Resource resource = resources.get(0);
      assertEquals(ResourceType.SOAP_UI_PROJECT, resource.getType());
      assertEquals("HelloServiceScriptBinding-1.0.xml", resource.getName());
      assertNotNull(resource.getContent());

      resource = resources.get(1);
      assertEquals(ResourceType.WSDL, resource.getType());
      assertEquals("HelloServiceScriptBinding-1.0.wsdl", resource.getName());
      assertNotNull(resource.getContent());

      // Check that messages have been correctly found.
      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (Exception e) {
         fail("No exception should be thrown when importing message definitions.");
      }
      assertEquals(2, exchanges.size());
      for (Exchange exchange : exchanges) {
         if (exchange instanceof RequestResponsePair) {
            RequestResponsePair entry = (RequestResponsePair) exchange;
            Request request = entry.getRequest();
            Response response = entry.getResponse();
            assertNotNull(request);
            assertNotNull(response);
            if ("Anne Request".equals(request.getName())) {
               assertEquals("Anne Response", response.getName());
               assertEquals("Anne Response", response.getDispatchCriteria());
            } else if ("Laurent Request".equals(request.getName())) {
               assertEquals("Laurent Response", response.getName());
               assertEquals("Laurent Response", response.getDispatchCriteria());
            }
         } else {
            fail("Exchange has the wrong type. Expecting RequestResponsePair");
         }
      }
   }

   @Test
   void testComplexProjectImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/RefTest-Product-GetProductElements-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("execute_pttBinding_GetProductElements MockService", service.getName());
      assertEquals("1.0.0", service.getVersion());

      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(3, resources.size());

      Resource resource = resources.get(0);
      assertEquals(ResourceType.SOAP_UI_PROJECT, resource.getType());
      assertNotNull(resource.getContent());
      assertEquals("execute_pttBinding_GetProductElements MockService-1.0.0.xml", resource.getName());

      resource = resources.get(1);
      assertEquals(ResourceType.XSD, resource.getType());
      assertNotNull(resource.getContent());
      assertEquals("Product_Anomalie_v1.0.xsd", resource.getName());

      resource = resources.get(2);
      assertEquals(ResourceType.WSDL, resource.getType());
      assertNotNull(resource.getContent());
      assertEquals("execute_pttBinding_GetProductElements MockService-1.0.0.wsdl", resource.getName());
      // Check that XSD path has been changed into WSDL.
      assertTrue(resource.getContent().contains(
            "<xsd:import namespace=\"http://lbroudoux.github.com/Product/Commun\" schemaLocation=\"./Product_Anomalie_v1.0.xsd\"/>"));
   }

   @Test
   void testSimpleRestProjectImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/Test-REST-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("Whabed MockService", service.getName());
      assertEquals("0.0.1", service.getVersion());

      // Check that resources import does not throw exceptions of failures.
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());

      // Check that operations and methods/resourcePaths have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations().toArray(new Operation[2])) {

         if ("/deployment".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertTrue(operation.getResourcePaths().contains("/deployment"));
            assertEquals("SEQUENCE", operation.getDispatcher());

         } else if ("/deployment/byComponent/{component}/{version}.json".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/deployment/byComponent/testREST/1.2.json"));
            assertTrue(operation.getResourcePaths().contains("/deployment/byComponent/testREST/1.3.json"));
            assertEquals("SEQUENCE", operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);

                  if ("deploymentsForTestREST1.2 Request".equals(request.getName())) {
                     assertEquals("deploymentsForTestREST1.2 Response", response.getName());
                     assertEquals("/component=testREST/version=1.2", response.getDispatchCriteria());
                  } else if ("deploymentsForTestREST1.3 Request".equals(request.getName())) {
                     assertEquals("deploymentsForTestREST1.3 Response", response.getName());
                     assertEquals("/component=testREST/version=1.3", response.getDispatchCriteria());
                  } else {
                     fail("Message has not an expected name");
                  }

                  assertEquals("application/json", response.getMediaType());
                  assertEquals("200", response.getStatus());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("/deployment/byEnvironment/{environment}/{qualifier}.json".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/deployment/byEnvironment/QUALIF2/cdsm.json"));
            assertEquals("SCRIPT", operation.getDispatcher());
            assertNotNull(operation.getDispatcherRules());
            assertFalse(operation.getDispatcherRules().isEmpty());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);

                  if ("deploymentsForQUALIF2cdsm Request".equals(request.getName())) {
                     assertNotNull(request.getQueryParameters());
                     assertEquals(4, request.getQueryParameters().size());
                     assertEquals("deploymentsForQUALIF2cdsm Response", response.getName());
                     assertEquals("deploymentsForQUALIF2cdsm Response", response.getDispatchCriteria());
                  } else if ("deploymentsForQUALIF2cdsm2 Request".equals(request.getName())) {
                     assertNotNull(request.getQueryParameters());
                     assertEquals(4, request.getQueryParameters().size());
                     assertEquals("deploymentsForQUALIF2cdsm2 Response", response.getName());
                     assertEquals("deploymentsForQUALIF2cdsm2 Response", response.getDispatchCriteria());
                  } else {
                     fail("Message has not an expected name");
                  }

                  assertEquals("application/json", response.getMediaType());
                  assertEquals("200", response.getStatus());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else {
            fail("Operation has not an expected name");
         }

      }
   }

   @Test
   void testSimpleProjectNoVersionImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/RefTest-no-version-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      boolean failure = false;
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         failure = true;
         assertNotEquals(-1, e.getMessage().indexOf("Version property"));
      }
      assertTrue(failure);
   }

   @Test
   void testHelloAPIProjectImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/HelloAPI-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
   }

   @Test
   void testMultipleInterfacesProjectImport() {
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/GetDrivers-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("DriverSoap", service.getName());
      assertEquals("http://www.itra.com", service.getXmlNS());
      assertEquals("1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(services.get(0));
      assertEquals(2, resources.size());
      Resource resource = resources.get(0);
      Assertions.assertEquals(ResourceType.SOAP_UI_PROJECT, resource.getType());
      assertEquals("DriverSoap-1.0.xml", resource.getName());
      assertNotNull(resource.getContent());

      resource = resources.get(1);
      Assertions.assertEquals(ResourceType.WSDL, resource.getType());
      assertEquals("DriverSoap-1.0.wsdl", resource.getName());
      assertNotNull(resource.getContent());
   }

   @Test
   void testPartNamesCorrectResolution() {
      // Initialized from https://github.com/microcks/microcks/issues/680.
      SoapUIProjectImporter importer = null;
      try {
         importer = new SoapUIProjectImporter(
               "target/test-classes/io/github/microcks/util/soapui/VIES-soapui-project.xml");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("VitaleServiceBinding-v2 Mock", service.getName());
      assertEquals("http://www.cnamts.fr/vitale/webservice/ServiceVitale/v2", service.getXmlNS());
      assertEquals("060000", service.getVersion());
      assertEquals(1, service.getOperations().size());

      Operation operation = service.getOperations().get(0);
      assertEquals("recupererSuiviParcoursCarte", operation.getName());
      assertEquals("urn:ServiceVitale:2:recupererSuiviParcoursCarte", operation.getAction());
      assertEquals("recupererSuiviParcoursCarteRequestElement", operation.getInputName());
      assertEquals("recupererSuiviParcoursCarteResponseElement", operation.getOutputName());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(services.get(0));
      assertEquals(3, resources.size());

      Resource resource = resources.get(1);
      Assertions.assertEquals(ResourceType.XSD, resource.getType());
      assertEquals("Suivi_Parcours_Carte_2.0.xsd", resource.getName());
   }

   @Test
   void testXsdReferenceReplacement() {
      // Test WSDL content with various XSD references
      String wsdlContent = """
         <?xml version="1.0" encoding="UTF-8"?>
         <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                          xmlns:xs="http://www.w3.org/2001/XMLSchema"
                          xmlns:tns="http://example.com/service"
                          targetNamespace="http://example.com/service">
            <wsdl:types>
               <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                          targetNamespace="http://example.com/service">
                  <xs:import namespace="http://example.com/types" 
                             schemaLocation="http://example.com/schemas/types.xsd"/>
                  <xs:include schemaLocation="http://example.com/schemas/common.xsd"/>
                  <xs:redefine schemaLocation="http://example.com/schemas/extensions.xsd">
                     <xs:complexType name="ExtendedType">
                        <xs:complexContent>
                           <xs:extension base="BaseType">
                              <xs:element name="additional" type="xs:string"/>
                           </xs:extension>
                        </xs:complexContent>
                     </xs:complexType>
                  </xs:redefine>
               </xs:schema>
            </wsdl:types>
            <wsdl:message name="RequestMessage">
               <wsdl:part name="body" element="tns:Request"/>
            </wsdl:message>
            <wsdl:message name="ResponseMessage">
               <wsdl:part name="body" element="tns:Response"/>
            </wsdl:message>
            <wsdl:portType name="TestService">
               <wsdl:operation name="testOperation">
                  <wsdl:input message="tns:RequestMessage"/>
                  <wsdl:output message="tns:ResponseMessage"/>
               </wsdl:operation>
            </wsdl:portType>
         </wsdl:definitions>
         """;

      // Test the replacement method directly using reflection
      try {
         SoapUIProjectImporter importer = new SoapUIProjectImporter("target/test-classes/io/github/microcks/util/soapui/HelloService-soapui-project.xml");
         
         // Use reflection to access the private method
         java.lang.reflect.Method replaceMethod = SoapUIProjectImporter.class.getDeclaredMethod(
            "replaceXsdReferencesInWsdl", String.class, String.class, String.class);
         replaceMethod.setAccessible(true);
         
         // Test replacement of types.xsd
         String result1 = (String) replaceMethod.invoke(importer, wsdlContent, 
            "http://example.com/schemas/types.xsd", "types.xsd");
         
         // Verify the replacement worked
         assertTrue(result1.contains("schemaLocation=\"./types.xsd\""), 
            "Should replace xs:import schemaLocation");
         assertFalse(result1.contains("schemaLocation=\"http://example.com/schemas/types.xsd\""), 
            "Should not contain original URL");
         
         // Test replacement of common.xsd
         String result2 = (String) replaceMethod.invoke(importer, result1, 
            "http://example.com/schemas/common.xsd", "common.xsd");
         
         // Verify the replacement worked
         assertTrue(result2.contains("schemaLocation=\"./common.xsd\""), 
            "Should replace xs:include schemaLocation");
         assertFalse(result2.contains("schemaLocation=\"http://example.com/schemas/common.xsd\""), 
            "Should not contain original URL");
         
         // Test replacement of extensions.xsd
         String result3 = (String) replaceMethod.invoke(importer, result2, 
            "http://example.com/schemas/extensions.xsd", "extensions.xsd");
         
         // Verify the replacement worked
         assertTrue(result3.contains("schemaLocation=\"./extensions.xsd\""), 
            "Should replace xs:redefine schemaLocation");
         assertFalse(result3.contains("schemaLocation=\"http://example.com/schemas/extensions.xsd\""), 
            "Should not contain original URL");
         
         // Verify all original content is preserved
         assertTrue(result3.contains("<wsdl:portType name=\"TestService\">"), 
            "Should preserve WSDL structure");
         assertTrue(result3.contains("<wsdl:operation name=\"testOperation\">"), 
            "Should preserve operation definitions");
         
      } catch (Exception e) {
         fail("Test failed with exception: " + e.getMessage());
      }
   }

   @Test
   void testXsdReferenceReplacementWithWindowsPaths() {
      // Test with Windows-style paths
      String wsdlContent = """
         <?xml version="1.0" encoding="UTF-8"?>
         <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                          xmlns:xs="http://www.w3.org/2001/XMLSchema"
                          xmlns:tns="http://example.com/service"
                          targetNamespace="http://example.com/service">
            <wsdl:types>
               <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                          targetNamespace="http://example.com/service">
                  <xs:import namespace="http://example.com/types" 
                             schemaLocation="C:\\schemas\\types.xsd"/>
                  <xs:include schemaLocation="D:\\common\\schemas\\common.xsd"/>
               </xs:schema>
            </wsdl:types>
         </wsdl:definitions>
         """;

      try {
         SoapUIProjectImporter importer = new SoapUIProjectImporter("target/test-classes/io/github/microcks/util/soapui/HelloService-soapui-project.xml");
         
         java.lang.reflect.Method replaceMethod = SoapUIProjectImporter.class.getDeclaredMethod(
            "replaceXsdReferencesInWsdl", String.class, String.class, String.class);
         replaceMethod.setAccessible(true);
         
         // Test replacement with Windows paths
         String result = (String) replaceMethod.invoke(importer, wsdlContent, 
            "C:\\schemas\\types.xsd", "types.xsd");
         
         // Verify the replacement worked
         assertTrue(result.contains("schemaLocation=\"./types.xsd\""), 
            "Should replace Windows path schemaLocation");
         assertFalse(result.contains("schemaLocation=\"C:\\schemas\\types.xsd\""), 
            "Should not contain original Windows path");
         
      } catch (Exception e) {
         fail("Test failed with exception: " + e.getMessage());
      }
   }
}
