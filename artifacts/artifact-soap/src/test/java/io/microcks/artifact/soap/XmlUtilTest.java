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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmlUtilTest {

   private Document document;
   private Element parent;

   @BeforeEach
   void setUp() throws ParserConfigurationException {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      document = builder.newDocument();

      parent = document.createElementNS("http://example.com", "parent");
      document.appendChild(parent);

      Element child1 = document.createElementNS("http://example.com", "child");
      parent.appendChild(child1);

      Element child2 = document.createElementNS("http://example.com", "child");
      parent.appendChild(child2);

      Element child3 = document.createElementNS("http://example.com", "otherChild");
      parent.appendChild(child3);
   }

   @Test
   void getDirectChildren() {
      List<Element> children = XmlUtil.getDirectChildren(parent, "http://example.com", "child");
      assertEquals(2, children.size());
      assertEquals("child", children.get(0).getLocalName());
      assertEquals("child", children.get(1).getLocalName());
   }

   @Test
   void getUniqueDirectChild() {
      assertThrows(MalformedXmlException.class, () -> {
         XmlUtil.getUniqueDirectChild(parent, "http://example.com", "child1");
      });

      Element uniqueChild = document.createElementNS("http://example.com", "uniqueChild");
      parent.appendChild(uniqueChild);

      try {
         Element retrievedChild = XmlUtil.getUniqueDirectChild(parent, "http://example.com", "uniqueChild");
         assertEquals("uniqueChild", retrievedChild.getLocalName());
      } catch (MalformedXmlException e) {
         fail("Exception should not have been thrown");
      }
   }

   @Test
   void hasDirectChild() {
      assertTrue(XmlUtil.hasDirectChild(parent, "http://example.com", "child"));
      assertFalse(XmlUtil.hasDirectChild(parent, "http://example.com", "nonExistentChild"));
   }
}
