package io.github.microcks.util.postman;

import io.github.microcks.domain.*;
import io.github.microcks.util.MockRepositoryImportException;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author laurent
 */
public class DBAPICollectionImporterTest {

   @Test
   public void testImportOriginal() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/DBAPI.postman_collection.json");
      } catch (IOException ioe) {
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
      assertEquals("dbapi", service.getName());
      assertEquals(ServiceType.REST, service.getType());

      for (Operation operation : service.getOperations()) {

         if ("GET ".equals(operation.getName())) {
            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(5, messages.size());
         }
         else if ("POST ".equals(operation.getName()) ){
            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, messages.size());
         }
      }
   }

   @Test
   public void testImportReorganised() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/DBAPI.reorganised.postman_collection.json");
      } catch (IOException ioe) {
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
      assertEquals("dbapi", service.getName());
      assertEquals(ServiceType.REST, service.getType());

      for (Operation operation : service.getOperations()) {

         // Check that messages have been correctly found.
         Map<Request, Response> messages = null;
         try {
            messages = importer.getMessageDefinitions(service, operation);
         } catch (Exception e) {
            e.printStackTrace();
            fail("No exception should be thrown when importing message definitions.");
         }
         System.err.println("  messages: " + messages.size());

         if ("GET /v1/addresses".equals(operation.getName())) {
            assertEquals(1, messages.size());
            Map.Entry<Request, Response> entry = messages.entrySet().iterator().next();
            Request request = entry.getKey();
            Response response = entry.getValue();
            assertNotNull(request);
            assertNotNull(response);

            assertEquals(2, request.getHeaders().size());
            assertEquals(15, response.getHeaders().size());
            assertEquals("200", response.getStatus());
            assertEquals("application/json", response.getMediaType());
         }
      }
   }
}
