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

import io.github.microcks.domain.*;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.ReferenceResolver;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class AsyncAPIImporter.
 * @author laurent
 */
class AsyncAPIImporterTest {

   @Test
   void testSimpleAsyncAPIImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml", null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleAsyncAPI(importer);
   }

   @Test
   void testSimpleAsyncAPIImportJSON() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.json", null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleAsyncAPI(importer);
   }

   private void importAndAssertOnSimpleAsyncAPI(AsyncAPIImporter importer) {
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
      assertEquals("0.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/signedup".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

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
                  assertEquals("application/json", eventMessage.getMediaType());

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

   @Test
   void testSimpleAsyncAPIImportYAMLWithExtensions() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/account-service-asyncapi.yaml", null);
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
      assertEquals("Account Service", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("1.0.0", service.getVersion());

      assertNotNull(service.getMetadata());
      assertEquals("authentication", service.getMetadata().getLabels().get("domain"));
      assertEquals("GA", service.getMetadata().getLabels().get("status"));
      assertEquals("Team B", service.getMetadata().getLabels().get("team"));

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/signedup".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());
            assertEquals(30, operation.getDefaultDelay().longValue());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testAsyncAPIImportGHMasterYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-gh-master.yaml", null);
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
      assertEquals("0.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/signedup".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

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
                  assertEquals("application/json", eventMessage.getMediaType());

                  if ("user/signedup-0".equals(eventMessage.getName())) {
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
                  } else if ("user/signedup-1".equals(eventMessage.getName())) {
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

   @Test
   void testAsyncAPI21ImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-2.1.yaml", null);
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
      assertEquals("0.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/signedup".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

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
                  assertEquals("application/json", eventMessage.getMediaType());

                  if ("Laurent".equals(eventMessage.getName())) {
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
                  } else if ("John".equals(eventMessage.getName())) {
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

   @Test
   void testAsyncAPI21RefImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/account-service-asyncapi.yaml", null);
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
      assertEquals("Account Service", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/signedup".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testAsyncAPI21MultiRefsImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-events-asyncapi-2.1.yaml", null);
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
      assertEquals("User events API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/events".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(3, exchanges.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testReferenceAsyncAPIImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/streetlights-asyncapi.yaml", null);
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
      assertEquals("Streetlights API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE smartylighting/streetlights/event/lighting/measured".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

            assertEquals(1, operation.getBindings().size());
            assertNotNull(operation.getBindings().get("MQTT"));

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(3, exchanges.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testJsonRemoteRelativeReferenceAsyncAPIImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-json-ref-asyncapi.yaml",
               new ReferenceResolver(
                     "https://raw.githubusercontent.com/microcks/microcks/1.7.x/webapp/src/test/resources/io/github/microcks/util/asyncapi/user-signedup-json-ref-asyncapi.yaml",
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
      assertEquals("0.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());

      // First resource is AsyncAPI spec itself.
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());
      assertTrue(resources.get(0).getContent().contains("$ref: \"User+signed-up+API-0.1.0-user-signedup.json\""));

      // Second resource is JSON SCHEMA spec itself.
      assertEquals(ResourceType.JSON_SCHEMA, resources.get(1).getType());
      assertEquals("User signed-up API-0.1.0-user-signedup.json", resources.get(1).getName());
      assertNotNull(resources.get(1).getContent());
   }

   @Test
   void testAvroAsyncAPIImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-asyncapi.yaml", null);
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
      assertEquals("User signed-up Avro API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.1.1", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/signedup".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

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
                  assertEquals("avro/binary", eventMessage.getMediaType());

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

   @Test
   void testAvroRemoteRefAsyncAPIImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-ref-asyncapi.yaml",
               new ReferenceResolver(
                     "https://raw.githubusercontent.com/microcks/microcks/master/webapp/src/test/resources/io/github/microcks/util/asyncapi/user-signedup-avro-ref-asyncapi.yaml",
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
      assertEquals("User signed-up Avro API", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.1.2", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());

      // First resource is AsyncAPI spec itself.
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Second resource should be the referenced relative Avro schema.
      assertEquals(ResourceType.AVRO_SCHEMA, resources.get(1).getType());
      assertEquals("User signed-up Avro API-0.1.2-user-signedup.avsc", resources.get(1).getName());
      assertNotNull(resources.get(1).getContent());
      assertTrue(resources.get(1).getContent().contains("\"namespace\": \"microcks.avro\""));

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("SUBSCRIBE user/signedup".equals(operation.getName())) {
            assertEquals("SUBSCRIBE", operation.getMethod());

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
                  assertEquals("avro/binary", eventMessage.getMediaType());

               } else {
                  fail("Exchange has the wrong type. Expecting UnidirectionalEvent");
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testAsyncAPIImportWithParametrizedChannel() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/api-maintenance.async-api-spec.yaml", null);
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
      assertEquals("ApiEventService:maintenance", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.0.3", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("PUBLISH apim/elevator-co/api/V1/json/{resource_region_id}/{equipmentType}/{eventType}/{resourceType}/{resourceId}"
               .equals(operation.getName())) {
            assertEquals("PUBLISH", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("resource_region_id && equipmentType && eventType && resourceType && resourceId",
                  operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths()
                  .contains("apim/elevator-co/api/V1/json/fr/elevator/maintenance/elev-make-1/abc4711"));
            assertTrue(operation.getResourcePaths()
                  .contains("apim/elevator-co/api/V1/json/de/elevator/maintenance/elev-make-2/xyz0815"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof UnidirectionalEvent) {
                  UnidirectionalEvent event = (UnidirectionalEvent) exchange;
                  EventMessage eventMessage = event.getEventMessage();

                  if ("misalignment".equals(eventMessage.getName())) {
                     assertEquals(
                           "/equipmentType=elevator/eventType=maintenance/resourceId=abc4711/resourceType=elev-make-1/resource_region_id=fr",
                           eventMessage.getDispatchCriteria());
                     assertEquals("application/json", eventMessage.getMediaType());
                     assertNotNull(eventMessage.getContent());
                  } else if ("doorfailure".equals(eventMessage.getName())) {
                     assertEquals(
                           "/equipmentType=elevator/eventType=maintenance/resourceId=xyz0815/resourceType=elev-make-2/resource_region_id=de",
                           eventMessage.getDispatchCriteria());
                     assertEquals("application/json", eventMessage.getMediaType());
                     assertNotNull(eventMessage.getContent());
                  } else {
                     fail("Event has the wrong name. Expecting misalignment or doorfailure");
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

   @Test
   void testAsyncAPIImportWithParametrizedChannelGHMaster() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(
               "target/test-classes/io/github/microcks/util/asyncapi/api-maintenance.async-api-spec-gh-master.yaml",
               null);
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
      assertEquals("ApiEventService:maintenance", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.0.3", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("PUBLISH apim/elevator-co/api/V1/json/{resource_region_id}/{equipmentType}/{eventType}/{resourceType}/{resourceId}"
               .equals(operation.getName())) {
            assertEquals("PUBLISH", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("resource_region_id && equipmentType && eventType && resourceType && resourceId",
                  operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths()
                  .contains("apim/elevator-co/api/V1/json/fr/elevator/maintenance/elev-make-1/abc4711"));
            assertTrue(operation.getResourcePaths()
                  .contains("apim/elevator-co/api/V1/json/de/elevator/maintenance/elev-make-2/xyz0815"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof UnidirectionalEvent) {
                  UnidirectionalEvent event = (UnidirectionalEvent) exchange;
                  EventMessage eventMessage = event.getEventMessage();

                  if ("apim/elevator-co/api/V1/json/{resource_region_id}/{equipmentType}/{eventType}/{resourceType}/{resourceId}-0"
                        .equals(eventMessage.getName())) {
                     assertEquals(
                           "/equipmentType=elevator/eventType=maintenance/resourceId=abc4711/resourceType=elev-make-1/resource_region_id=fr",
                           eventMessage.getDispatchCriteria());
                     assertEquals("application/json", eventMessage.getMediaType());
                     assertNotNull(eventMessage.getContent());
                  } else if ("apim/elevator-co/api/V1/json/{resource_region_id}/{equipmentType}/{eventType}/{resourceType}/{resourceId}-1"
                        .equals(eventMessage.getName())) {
                     assertEquals(
                           "/equipmentType=elevator/eventType=maintenance/resourceId=xyz0815/resourceType=elev-make-2/resource_region_id=de",
                           eventMessage.getDispatchCriteria());
                     assertEquals("application/json", eventMessage.getMediaType());
                     assertNotNull(eventMessage.getContent());
                  } else {
                     fail("Event has the wrong name. Expecting apim/elevator-co/api/V1/json/{resource_region_id}/{equipmentType}/{eventType}/{resourceType}/{resourceId}-0"
                           + " or apim/elevator-co/api/V1/json/{resource_region_id}/{equipmentType}/{eventType}/{resourceType}/{resourceId}-1");
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

   @ParameterizedTest
   @ValueSource(strings = {
         "target/test-classes/io/github/microcks/util/asyncapi/api-maintenance.async-api-spec-ws.yaml",
         "target/test-classes/io/github/microcks/util/asyncapi/api-maintenance.async-api-spec-ws-kv.yaml" })
   void testAsyncAPIImportWithCorrectlyParametrizedChannel(String asyncAPISpecPath) {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter(asyncAPISpecPath, null);
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
      assertEquals("ApiEventService:maintenance", service.getName());
      assertEquals(ServiceType.EVENT, service.getType());
      assertEquals("0.0.3", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.ASYNC_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("PUBLISH elevator-co/{resource_region_id}/{equipmentType}/{eventType}/{resourceType}/{resourceId}"
               .equals(operation.getName())) {
            assertEquals("PUBLISH", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("resource_region_id && equipmentType && eventType && resourceType && resourceId",
                  operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(
                  operation.getResourcePaths().contains("elevator-co/fr/elevator/maintenance/elev-make-1/abc4711"));
            assertTrue(
                  operation.getResourcePaths().contains("elevator-co/de/elevator/maintenance/elev-make-2/xyz0815"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof UnidirectionalEvent) {
                  UnidirectionalEvent event = (UnidirectionalEvent) exchange;
                  EventMessage eventMessage = event.getEventMessage();

                  if ("misalignment".equals(eventMessage.getName())) {
                     assertEquals(
                           "/equipmentType=elevator/eventType=maintenance/resourceId=abc4711/resourceType=elev-make-1/resource_region_id=fr",
                           eventMessage.getDispatchCriteria());
                     assertEquals("application/json", eventMessage.getMediaType());
                     assertNotNull(eventMessage.getContent());
                  } else if ("doorfailure".equals(eventMessage.getName())) {
                     assertEquals(
                           "/equipmentType=elevator/eventType=maintenance/resourceId=xyz0815/resourceType=elev-make-2/resource_region_id=de",
                           eventMessage.getDispatchCriteria());
                     assertEquals("application/json", eventMessage.getMediaType());
                     assertNotNull(eventMessage.getContent());
                  } else {
                     fail("Event has the wrong name. Expecting misalignment or doorfailure");
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
