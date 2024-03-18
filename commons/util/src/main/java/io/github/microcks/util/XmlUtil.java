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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods for parsing/navigating an Xml DOM.
 * @author laurent
 */
public class XmlUtil {

   /** XML Schema public namespace; */
   public static final String XML_SCHEMA_NS = "http://www.w3.org/2001/XMLSchema";
   /** WSDL public namespace. */
   public static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/";

   private XmlUtil() {
      // Hide the implicit default constructor.
   }

   /**
    * Retrieve direct children elements of a parent having specified namespace and tag. Only includes level 1 children.
    * @param parent    The parent of children to find
    * @param namespace The namespace of children
    * @param tag       The tag of children
    * @return Children Elements as a list
    */
   public static List<Element> getDirectChildren(Element parent, String namespace, String tag) {
      List<Element> directChildren = new ArrayList<>();
      NodeList children = parent.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);
         if (child.getNodeType() == Node.ELEMENT_NODE && namespace.equals(child.getNamespaceURI())
               && tag.equals(child.getLocalName())) {
            directChildren.add((Element) child);
         }
      }
      return directChildren;
   }

   /**
    * Retrieve a direct child that is expected to be unique under the parent. Throws a MalformedXmlException if no child
    * or more than one child present.
    * @param parent    The parent of child to find
    * @param namespace The namespace of child
    * @param tag       The tag of child
    * @return The child Element
    * @throws MalformedXmlException if no child or more than one child present.
    */
   public static Element getUniqueDirectChild(Element parent, String namespace, String tag)
         throws MalformedXmlException {
      NodeList children = parent.getElementsByTagNameNS(namespace, tag);
      if (children.getLength() == 0) {
         throw new MalformedXmlException("Element " + tag + " is missing under " + parent.getTagName());
      }
      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);
         if (child.getNodeType() == Node.ELEMENT_NODE && child.getParentNode() == parent) {
            return (Element) child;
         }
      }
      throw new MalformedXmlException("Element " + tag + " was expected directly under " + parent.getTagName());
   }

   /**
    * Check if parent has at least one direct child having namespace and tag.
    * @param parent    The parent of children to find
    * @param namespace The namespace of children
    * @param tag       The tag of children
    * @return true if at least one child is present, false otherwise
    */
   public static boolean hasDirectChild(Element parent, String namespace, String tag) {
      NodeList children = parent.getElementsByTagNameNS(namespace, tag);
      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);
         if (child.getNodeType() == Node.ELEMENT_NODE && child.getParentNode() == parent) {
            return true;
         }
      }
      return false;
   }
}
