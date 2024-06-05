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

package io.github.microcks.util.postman;

import io.github.microcks.domain.*;
import io.github.microcks.util.MockRepositoryImportException;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author laurent
 */
class DBAPICollectionImporterTest {

   @Test
   void testImportOriginal() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/DBAPI.postman_collection.json");
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
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(5, exchanges.size());
         } else if ("POST ".equals(operation.getName())) {
            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, exchanges.size());
         }
      }
   }

   @Test
   void testImportReorganised() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/DBAPI.reorganised.postman_collection.json");
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
         List<Exchange> exchanges = null;
         try {
            exchanges = importer.getMessageDefinitions(service, operation);
         } catch (Exception e) {
            fail("No exception should be thrown when importing message definitions.");
         }

         if ("GET /v1/addresses".equals(operation.getName())) {
            assertEquals(1, exchanges.size());
            Exchange exchange = exchanges.get(0);
            if (exchange instanceof RequestResponsePair) {
               RequestResponsePair entry = (RequestResponsePair) exchange;
               Request request = entry.getRequest();
               Response response = entry.getResponse();
               assertNotNull(request);
               assertNotNull(response);

               assertEquals(2, request.getHeaders().size());
               assertEquals(15, response.getHeaders().size());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         }
      }
   }
}
