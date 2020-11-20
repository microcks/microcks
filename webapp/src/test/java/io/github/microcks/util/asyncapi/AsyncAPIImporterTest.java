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
package io.github.microcks.util.asyncapi;

import io.github.microcks.domain.*;
import io.github.microcks.util.MockRepositoryImportException;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a test case for class AsyncAPIImporter.
 * @author laurent
 */
public class AsyncAPIImporterTest {

   @Test
   public void testSimpleAsyncAPIImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml");
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
                  UnidirectionalEvent event = (UnidirectionalEvent)exchange;
                  EventMessage eventMessage = event.getEventMessage();
                  assertNotNull(eventMessage);

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
   public void testReferenceAsyncAPIImportYAML() {
      AsyncAPIImporter importer = null;
      try {
         importer = new AsyncAPIImporter("target/test-classes/io/github/microcks/util/asyncapi/streetlights-asyncapi.yaml");
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
}
