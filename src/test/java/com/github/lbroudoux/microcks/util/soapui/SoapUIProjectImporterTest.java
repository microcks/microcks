package com.github.lbroudoux.microcks.util.soapui;

import static org.junit.Assert.*;

import com.github.lbroudoux.microcks.domain.Request;
import com.github.lbroudoux.microcks.domain.Resource;
import com.github.lbroudoux.microcks.domain.ResourceType;
import com.github.lbroudoux.microcks.domain.Response;
import com.github.lbroudoux.microcks.domain.Service;
import com.github.lbroudoux.microcks.domain.Operation;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
/**
 * This is a test case for class SoapUIProjectImporter.
 * @author laurent
 */
public class SoapUIProjectImporterTest {
   
   @Test
   public void testSimpleProjectImport(){
      SoapUIProjectImporter importer = null;
      try{
         importer = new SoapUIProjectImporter("target/test-classes/com/github/lbroudoux/microcks/util/soapui/RefTest-soapui-project.xml");
      } catch (Exception e){
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = importer.getServiceDefinitions();
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
      assertTrue(operation.getDispatcherRules().contains("declare namespace ser='http://lbroudoux.github.com/test/service';"));
      assertTrue(operation.getDispatcherRules().contains("//ser:sayHello/name"));
      
      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      Resource resource = resources.get(0);
      assertEquals(ResourceType.WSDL, resource.getType());
      assertEquals("HelloServiceSoapBinding-1.2.wsdl", resource.getName());
      assertNotNull(resource.getContent());
      
      // Check that messages have been correctly found.
      Map<Request, Response> messages = null;
      try{
         messages = importer.getMessageDefinitions(service, operation);
      } catch (Exception e){
         fail("No exception should be thrown when importing message definitions.");
      }
      assertEquals(2, messages.size());
      for (Entry<Request, Response> entry : messages.entrySet()){
         Request request = entry.getKey();
         Response response = entry.getValue();
         assertNotNull(request);
         assertNotNull(response);
         if ("Anne Request".equals(request.getName())){
            assertEquals(3, request.getHeaders().size());
            assertEquals("Anne Response", response.getName());
            assertEquals("Anne", response.getDispatchCriteria());
         }
         else if ("Laurent Request".equals(request.getName())){
            assertEquals("Laurent Response", response.getName());
            assertEquals("Laurent", response.getDispatchCriteria());
         }
      }
   }
   
   @Test
   public void testSimpleScriptProjectImport(){
      SoapUIProjectImporter importer = null;
      try{
         importer = new SoapUIProjectImporter("target/test-classes/com/github/lbroudoux/microcks/util/soapui/RefTest-script-soapui-project.xml");
      } catch (Exception e){
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = importer.getServiceDefinitions();
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("HelloServiceScriptBinding", service.getName());
      assertEquals("http://lbroudoux.github.com/test/service", service.getXmlNS());
      assertEquals("1.0", service.getVersion());
      
      // Check that operations and and input/output have been found.
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
      assertEquals(1, resources.size());
      Resource resource = resources.get(0);
      assertEquals(ResourceType.WSDL, resource.getType());
      assertEquals("HelloServiceScriptBinding-1.0.wsdl", resource.getName());
      assertNotNull(resource.getContent());
      
      // Check that messages have been correctly found.
      Map<Request, Response> messages = null;
      try{
         messages = importer.getMessageDefinitions(service, operation);
      } catch (Exception e){
         fail("No exception should be thrown when importing message definitions.");
      }
      assertEquals(2, messages.size());
      for (Entry<Request, Response> entry : messages.entrySet()){
         Request request = entry.getKey();
         Response response = entry.getValue();
         assertNotNull(request);
         assertNotNull(response);
         if ("Anne Request".equals(request.getName())){
            assertEquals("Anne Response", response.getName());
            assertEquals("Anne Response", response.getDispatchCriteria());
         }
         else if ("Laurent Request".equals(request.getName())){
            assertEquals("Laurent Response", response.getName());
            assertEquals("Laurent Response", response.getDispatchCriteria());
         }
      }
   }
   
   @Test
   public void testComplexProjectImport(){
      SoapUIProjectImporter importer = null;
      try{
         importer = new SoapUIProjectImporter("target/test-classes/com/github/lbroudoux/microcks/util/soapui/RefTest-Product-GetProductElements-soapui-project.xml");
      } catch (Exception e){
         fail("Exception should not be thrown");
      }
      List<Service> services = importer.getServiceDefinitions();
      assertEquals(1, services.size());
      Service service = services.get(0);
      
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());
      Resource resource = resources.get(0);
      assertEquals(ResourceType.XSD, resource.getType());
      assertNotNull(resource.getContent());
      assertEquals("Product_Anomalie_v1.0.xsd", resource.getName());
      
      resource = resources.get(1);
      assertEquals(ResourceType.WSDL, resource.getType());
      assertNotNull(resource.getContent());
      assertEquals("execute_pttBinding_GetProductElements MockService-null.wsdl", resource.getName());
      // Check that XSD path has been changed into WSDL.
      assertTrue(resource.getContent().contains("<xsd:import namespace=\"http://lbroudoux.github.com/Product/Commun\" schemaLocation=\"./Product_Anomalie_v1.0.xsd\"/>"));
   }
   
   @Test
   public void testSimpleRestProjectImport(){
      SoapUIProjectImporter importer = null;
      try{
         importer = new SoapUIProjectImporter("target/test-classes/com/github/lbroudoux/microcks/util/soapui/Test-REST-soapui-project.xml");
      } catch (Exception e){
         fail("Exception should not be thrown");
      }
      // Check that basic service properties are there.
      List<Service> services = importer.getServiceDefinitions();
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("Whabed MockService", service.getName());
      assertEquals("0.0.1", service.getVersion());
      
      // Check that resources import does not throw exceptions of failures.
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(0, resources.size());
      
      // Check that operations and methods/resourcePaths have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations().toArray(new Operation[2])){
         
         if ("/deployment".equals(operation.getName())){
            assertEquals("POST", operation.getMethod());
            assertTrue(operation.getResourcePaths().contains("/deployment"));
            assertEquals("SEQUENCE", operation.getDispatcher());
            
         } 
         else if ("/deployment/byComponent/{component}/{version}.json".equals(operation.getName())){
            assertEquals("GET", operation.getMethod());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/deployment/byComponent/testREST/1.2.json"));
            assertTrue(operation.getResourcePaths().contains("/deployment/byComponent/testREST/1.3.json"));
            assertEquals("SEQUENCE", operation.getDispatcher());
            
            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try{
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e){
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, messages.size());
            for (Entry<Request, Response> entry : messages.entrySet()){
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               
               if ("deploymentsForTestREST1.2 Request".equals(request.getName())){
                  assertEquals("deploymentsForTestREST1.2 Response", response.getName());
                  assertEquals("/component=testREST/version=1.2", response.getDispatchCriteria());
               }
               else if ("deploymentsForTestREST1.3 Request".equals(request.getName())){
                  assertEquals("deploymentsForTestREST1.3 Response", response.getName());
                  assertEquals("/component=testREST/version=1.3", response.getDispatchCriteria());
               }
               else {
                  fail("Message has not an expected name");
               }
               
               assertEquals("application/json", response.getMediaType());
               assertEquals("200", response.getStatus());
            }
            
         } 
         else if ("/deployment/byEnvironment/{environment}/{qualifier}.json".equals(operation.getName())){
            assertEquals("GET", operation.getMethod());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/deployment/byEnvironment/QUALIF2/cdsm.json"));
            assertEquals("SCRIPT", operation.getDispatcher());
            assertNotNull(operation.getDispatcherRules());
            assertFalse(operation.getDispatcherRules().isEmpty());
            
            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try{
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e){
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, messages.size());
            for (Entry<Request, Response> entry : messages.entrySet()){
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               
               if ("deploymentsForQUALIF2cdsm Request".equals(request.getName())){
                  assertNotNull(request.getQueryParameters());
                  assertEquals(4, request.getQueryParameters().size());
                  assertEquals("deploymentsForQUALIF2cdsm Response", response.getName());
                  assertEquals("deploymentsForQUALIF2cdsm Response", response.getDispatchCriteria());
               }
               else if ("deploymentsForQUALIF2cdsm2 Request".equals(request.getName())){
                  assertNotNull(request.getQueryParameters());
                  assertEquals(4, request.getQueryParameters().size());
                  assertEquals("deploymentsForQUALIF2cdsm2 Response", response.getName());
                  assertEquals("deploymentsForQUALIF2cdsm2 Response", response.getDispatchCriteria());
               }
               else {
                  fail("Message has not an expected name");
               }
               
               assertEquals("application/json", response.getMediaType());
               assertEquals("200", response.getStatus());
            }
         } 
         else {
            fail("Operation has not an expected name");
         }
         
      }
   }
}
