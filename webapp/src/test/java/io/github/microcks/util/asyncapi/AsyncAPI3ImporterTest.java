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

package io.github.microcks.util.asyncapi;

import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.ReferenceResolver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class AsyncAPI3Importer.
 * @author laurent
 */
class AsyncAPI3ImporterTest {

   @Test
   void testSimpleAsyncAPI3ImportYAML() {
      AsyncAPI3Importer importer = null;
      try {
         importer = new AsyncAPI3Importer(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0.yaml", null);
      } catch (IOException ioe) {
         Assertions.fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         Assertions.fail("Exception should not be thrown");
      }

      Assertions.assertEquals(1, services.size());
      Service service = services.get(0);
      Assertions.assertEquals("User signed-up API", service.getName());
      Assertions.assertEquals(ServiceType.EVENT, service.getType());
      Assertions.assertEquals("0.3.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      Assertions.assertEquals(1, resources.size());
      Assertions.assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      Assertions.assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      Assertions.assertNotNull(resources.get(0).getContent());

      importAndAssertOnSimpleAsyncAPI(service, importer, "application/json");
   }

   @Test
   void testSimpleNamelessAsyncAPI3ImportYAML() {
      AsyncAPI3Importer importer = null;
      try {
         importer = new AsyncAPI3Importer(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-nameless.yaml", null);
      } catch (IOException ioe) {
         Assertions.fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         Assertions.fail("Exception should not be thrown");
      }

      Assertions.assertEquals(1, services.size());
      Service service = services.get(0);

      // Check that operations and input/output have been found.
      Assertions.assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {
         if ("SEND publishUserSignedUps".equals(operation.getName())) {
            Assertions.assertEquals("SEND", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               Assertions.fail("No exception should be thrown when importing message definitions.");
            }
            Assertions.assertEquals(2, exchanges.size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof UnidirectionalEvent event) {
                  EventMessage eventMessage = event.getEventMessage();
                  Assertions.assertNotNull(eventMessage);

                  Assertions.assertTrue("userSignedUp-1".equals(eventMessage.getName())
                        || "userSignedUp-2".equals(eventMessage.getName()));
               }
            }
         } else {
            Assertions.fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testStaticParametersAsyncAPI3ImportYAML() {
      AsyncAPI3Importer importer = null;
      try {
         importer = new AsyncAPI3Importer(
               "target/test-classes/io/github/microcks/util/asyncapi/streetlights-asyncapi-3.0-static.yaml", null);
      } catch (IOException ioe) {
         Assertions.fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         Assertions.fail("Exception should not be thrown");
      }

      Assertions.assertEquals(1, services.size());
      Service service = services.get(0);
      Assertions.assertEquals("Streetlights Kafka API", service.getName());
      Assertions.assertEquals(ServiceType.EVENT, service.getType());
      Assertions.assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      Assertions.assertEquals(1, resources.size());
      Assertions.assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      Assertions.assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      Assertions.assertNotNull(resources.get(0).getContent());

      // Check operations.
      Assertions.assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         Assertions.assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
         Assertions.assertEquals("streetlightId", operation.getDispatcherRules());
         Assertions.assertNotNull(operation.getResourcePaths());
         Assertions.assertEquals(1, operation.getResourcePaths().size());

         if ("RECEIVE receiveLightMeasurement".equals(operation.getName())) {
            Assertions.assertEquals("RECEIVE", operation.getMethod());
            Assertions.assertTrue(operation.getResourcePaths()
                  .contains("smartylighting.streetlights.1.0.event.{streetlightId}.lighting.measured"));

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               Assertions.fail("No exception should be thrown when importing message definitions.");
            }
            Assertions.assertEquals(1, exchanges.size());

            // Check resource paths have been discovered.
            Assertions.assertEquals(2, operation.getResourcePaths().size());
            Assertions.assertTrue(
                  operation.getResourcePaths().contains("smartylighting.streetlights.1.0.event.123.lighting.measured"));

            Exchange exchange = exchanges.get(0);
            Assertions.assertTrue(exchange instanceof UnidirectionalEvent);
            EventMessage message = ((UnidirectionalEvent) exchange).getEventMessage();

            assertEquals("Sample", message.getName());
            assertEquals("/streetlightId=123", message.getDispatchCriteria());
            assertNotNull(message.getContent());
            assertTrue(message.getContent().contains("100"));

         } else if ("SEND turnOn".equals(operation.getName())) {
            assertEquals("SEND", operation.getMethod());
         } else if ("SEND turnOff".equals(operation.getName())) {
            assertEquals("SEND", operation.getMethod());
         } else if ("SEND dimLight".equals(operation.getName())) {
            assertEquals("SEND", operation.getMethod());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testDynamicParametersAsyncAPI3ImportYAML() {
      AsyncAPI3Importer importer = null;
      try {
         importer = new AsyncAPI3Importer(
               "target/test-classes/io/github/microcks/util/asyncapi/streetlights-asyncapi-3.0-dynamic.yaml", null);
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
      assertEquals("Streetlights Kafka API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check operations.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
         assertEquals("streetlightId", operation.getDispatcherRules());
         assertNotNull(operation.getResourcePaths());
         assertEquals(1, operation.getResourcePaths().size());

         if ("RECEIVE receiveLightMeasurement".equals(operation.getName())) {
            assertEquals("RECEIVE", operation.getMethod());
            assertTrue(operation.getResourcePaths()
                  .contains("smartylighting.streetlights.1.0.event.{streetlightId}.lighting.measured"));

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());

            // Check resource paths have been discovered.
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains(
                  "smartylighting.streetlights.1.0.event.da059782-3ad0-4e45-88ce-ef3392bc7797.lighting.measured"));

            Exchange exchange = exchanges.get(0);
            assertTrue(exchange instanceof UnidirectionalEvent);
            EventMessage message = ((UnidirectionalEvent) exchange).getEventMessage();

            assertEquals("Sample", message.getName());
            assertEquals("/streetlightId=da059782-3ad0-4e45-88ce-ef3392bc7797", message.getDispatchCriteria());
            assertNotNull(message.getContent());
            assertTrue(message.getContent().contains("100"));

         } else if ("SEND turnOn".equals(operation.getName())) {
            assertEquals("SEND", operation.getMethod());
         } else if ("SEND turnOff".equals(operation.getName())) {
            assertEquals("SEND", operation.getMethod());
         } else if ("SEND dimLight".equals(operation.getName())) {
            assertEquals("SEND", operation.getMethod());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testRemoteRelativeReferenceAsyncAPI3ImportYAML() {
      AsyncAPI3Importer importer = null;
      try {
         importer = new AsyncAPI3Importer(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-ref.yaml",
               new ReferenceResolver(
                     "https://raw.githubusercontent.com/microcks/microcks/1.9.x/webapp/src/test/resources/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-ref.yaml",
                     null, true));
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
      assertEquals("User signed-up API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.3.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(3, resources.size());

      Resource asyncAPISpec = resources.get(0);
      assertEquals(ResourceType.ASYNC_API_SPEC, asyncAPISpec.getType());
      assertTrue(asyncAPISpec.getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(asyncAPISpec.getContent());

      // Check references have been denormalized and replaced in content.
      assertTrue(asyncAPISpec.getContent()
            .contains("User+signed-up+API-0.3.0--user-signedup-commons.yaml#/components/messageTraits/commonHeaders"));
      assertTrue(asyncAPISpec.getContent()
            .contains("User+signed-up+API-0.3.0--user-signedup-schemas.yaml#/components/schemas/UserInfo"));

      for (int i = 1; i < 3; i++) {
         Resource refResource = resources.get(i);
         assertNotNull(refResource.getContent());
         if ("User signed-up API-0.3.0--user-signedup-commons.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals("./user-signedup-commons.yaml", refResource.getPath());
         } else if ("User signed-up API-0.3.0--user-signedup-schemas.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals("./user-signedup-schemas.yaml", refResource.getPath());
         } else {
            fail("Unknown ref resource found");
         }
      }

      importAndAssertOnSimpleAsyncAPI(service, importer, "application/json");

      // Check binding has been parsed.
      Operation operation = service.getOperations().get(0);
      assertNotNull(operation.getBindings().get(BindingType.WS.name()));
      assertEquals("POST", operation.getBindings().get(BindingType.WS.name()).getMethod());

      // Check x-microcks-operation extension has been parsed.
      assertEquals(30L, operation.getDefaultDelay().longValue());
   }

   @Test
   void testRemoteRelativeAvroReferenceAsyncAPI3ImportYAML() {
      AsyncAPI3Importer importer = null;
      try {
         importer = new AsyncAPI3Importer(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-avro-ref.yaml",
               new ReferenceResolver(
                     "https://raw.githubusercontent.com/microcks/microcks/1.9.x/webapp/src/test/resources/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-avro-ref.yaml",
                     null, true));
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
      assertEquals("User signed-up API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.3.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(3, resources.size());

      Resource asyncAPISpec = resources.get(0);
      assertEquals(ResourceType.ASYNC_API_SPEC, asyncAPISpec.getType());
      assertTrue(asyncAPISpec.getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(asyncAPISpec.getContent());

      // Check references have been denormalized and replaced in content.
      assertTrue(asyncAPISpec.getContent()
            .contains("User+signed-up+API-0.3.0--user-signedup-commons.yaml#/components/messageTraits/commonHeaders"));
      assertTrue(asyncAPISpec.getContent().contains("User+signed-up+API-0.3.0--user-signedup.avsc#/User"));

      for (int i = 1; i < 3; i++) {
         Resource refResource = resources.get(i);
         assertNotNull(refResource.getContent());
         if ("User signed-up API-0.3.0--user-signedup-commons.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals("./user-signedup-commons.yaml", refResource.getPath());
            assertTrue(refResource.getContent().contains("commonHeaders:"));
         } else if ("User signed-up API-0.3.0--user-signedup.avsc".equals(refResource.getName())) {
            assertEquals(ResourceType.AVRO_SCHEMA, refResource.getType());
            assertEquals("./user-signedup.avsc", refResource.getPath());
            assertTrue(refResource.getContent().contains("\"namespace\": \"microcks.avro\""));
         } else {
            fail("Unknown ref resource found");
         }
      }

      importAndAssertOnSimpleAsyncAPI(service, importer, "avro/binary");
   }

   @Test
   void testRemoteAbsoluteAvroReferenceAsyncAPI3ImportYAML() {
      AsyncAPI3Importer importer = null;
      try {
         importer = new AsyncAPI3Importer(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-avro-absolute-ref.yaml",
               new ReferenceResolver(
                     "https://raw.githubusercontent.com/microcks/microcks/1.9.x/webapp/src/test/resources/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0-avro-absolute-ref.yaml",
                     null, true));
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
      assertEquals("User signed-up API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.3.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(3, resources.size());

      Resource asyncAPISpec = resources.get(0);
      assertEquals(ResourceType.ASYNC_API_SPEC, asyncAPISpec.getType());
      assertTrue(asyncAPISpec.getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(asyncAPISpec.getContent());

      // Check references have been denormalized and replaced in content.
      assertTrue(asyncAPISpec.getContent()
            .contains("User+signed-up+API-0.3.0--user-signedup-commons.yaml#/components/messageTraits/commonHeaders"));
      //assertTrue(asyncAPISpec.getContent().contains("User+signed-up+API-0.3.0--user-signedup.avsc#/User"));

      for (int i = 1; i < 3; i++) {
         Resource refResource = resources.get(i);

         if ("User signed-up API-0.3.0--user-signedup-commons.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals("./user-signedup-commons.yaml", refResource.getPath());
            assertNotNull(refResource.getContent());
         } else if ("User signed-up API-0.3.0-user-signedup.avsc".equals(refResource.getName())) {
            assertEquals(ResourceType.AVRO_SCHEMA, refResource.getType());
            assertEquals(
                  "https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/asyncapi/user-signedup.avsc",
                  refResource.getPath());
            assertNotNull(refResource.getContent());
         } else {
            System.err.println(refResource.getName());
            fail("Unknown ref resource found");
         }
      }

      importAndAssertOnSimpleAsyncAPI(service, importer, "avro/binary");
   }

   private void importAndAssertOnSimpleAsyncAPI(Service service, AsyncAPI3Importer importer, String contentType) {
      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {
         if ("SEND publishUserSignedUps".equals(operation.getName())) {
            assertEquals("SEND", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof UnidirectionalEvent) {
                  UnidirectionalEvent event = (UnidirectionalEvent) exchange;
                  EventMessage eventMessage = event.getEventMessage();
                  assertNotNull(eventMessage);
                  assertEquals(contentType, eventMessage.getMediaType());

                  if ("laurent".equals(eventMessage.getName())) {
                     assertNotNull(eventMessage.getContent());
                     assertTrue(eventMessage.getContent().contains("Laurent Broudoux"));
                     assertNotNull(eventMessage.getHeaders());
                     assertEquals(2, eventMessage.getHeaders().size());

                     Iterator<Header> headers = eventMessage.getHeaders().iterator();
                     while (headers.hasNext()) {
                        Header header = headers.next();
                        if ("my-app-header".equals(header.getName())) {
                           assertEquals("23", header.getValues().iterator().next());
                        } else if ("sentAt".equals(header.getName())) {
                           assertEquals("2020-03-11T08:03:28Z", header.getValues().iterator().next());
                        } else {
                           fail("Unknown header name: " + header.getName());
                        }
                     }
                  } else if ("john".equals(eventMessage.getName())) {
                     assertNotNull(eventMessage.getContent());
                     assertTrue(eventMessage.getContent().contains("John Doe"));
                     assertNotNull(eventMessage.getHeaders());
                     assertEquals(2, eventMessage.getHeaders().size());

                     Iterator<Header> headers = eventMessage.getHeaders().iterator();
                     while (headers.hasNext()) {
                        Header header = headers.next();
                        if ("my-app-header".equals(header.getName())) {
                           assertEquals("24", header.getValues().iterator().next());
                        } else if ("sentAt".equals(header.getName())) {
                           assertEquals("2020-03-11T08:03:38Z", header.getValues().iterator().next());
                        } else {
                           fail("Unknown header name: " + header.getName());
                        }
                     }
                  } else {
                     fail("Unknown message name: " + eventMessage.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting UnidirectionalEvent");
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }
}
