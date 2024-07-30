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
package io.github.microcks.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.ls.LSInput;

import static org.junit.jupiter.api.Assertions.*;

class XmlSchemaURLResolverTest {

   private static final String BASE_RESOURCE_URL = "http://example.com/schemas";
   private XmlSchemaURLResolver resolver;

   @BeforeEach
   public void setUp() {
      resolver = new XmlSchemaURLResolver(BASE_RESOURCE_URL);
   }

   @Test
   void testResolveResourceWithLocalResolution() {
      String systemId = "http://www.w3.org/2001/xml.xsd";

      LSInput lsInput = resolver.resolveResource(null, null, null, systemId, null);

      assertNotNull(lsInput);
      assertEquals(systemId, lsInput.getSystemId());
      assertNotNull(lsInput.getCharacterStream());
   }

   //   @Test
   //   void testResolveResourceWithBaseResourceURL() throws Exception {
   //      String systemId = "test.xsd";
   //      String expectedContent = "<schema></schema>";
   //
   //      URL mockURL = mock(URL.class);
   //      URLConnection mockConnection = mock(URLConnection.class);
   //      InputStream mockInputStream = new ByteArrayInputStream(expectedContent.getBytes());
   //
   //      when(mockURL.openStream()).thenReturn(mockInputStream);
   //      when(mockConnection.getInputStream()).thenReturn(mockInputStream);
   //
   //      // Instead of mocking the URL constructor, we can mock URL.openStream directly.
   //      URL.setURLStreamHandlerFactory(protocol -> {
   //         return new java.net.URLStreamHandler() {
   //            @Override
   //            protected URLConnection openConnection(URL url) {
   //               return mockConnection;
   //            }
   //         };
   //      });
   //
   //      LSInput lsInput = resolver.resolveResource(null, null, null, systemId, null);
   //
   //      assertNotNull(lsInput);
   //      assertEquals(systemId, lsInput.getSystemId());
   //      assertNotNull(lsInput.getCharacterStream());
   //   }

   @Test
   void testResolveResourceWithNonExistentResource() {
      String systemId = "non-existent.xsd";

      LSInput lsInput = resolver.resolveResource(null, null, null, systemId, null);

      assertNull(lsInput);
   }
}

