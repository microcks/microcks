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

import io.github.microcks.util.MalformedXmlException;
import io.github.microcks.util.XmlUtil;

import org.w3c.dom.Element;
import java.util.List;

/**
 * Helper class for parsing SoapUI project files.
 * @author laurent
 */
public class SoapUIProjectParserUtils {

   /** The SoapUI namespace for configuration found in project files. */
   private static final String SOAPUI_CONFIG_NS = "http://eviware.com/soapui/config";

   /**
    * Retrieve direct children elements of a parent in SoapUI config and tag. Only includes level 1 children.
    * @param parent The parent of children to find
    * @param tag    The tag of children
    * @return Children Elements as a list
    */
   public static List<Element> getConfigDirectChildren(Element parent, String tag) {
      return XmlUtil.getDirectChildren(parent, SOAPUI_CONFIG_NS, tag);
   }

   /**
    * Retrieve a direct child that is expected to be unique under the parent. Throws a MalformedXmlException if no child
    * or more than one child present.
    * @param parent The parent of child to find
    * @param tag    The tag of child
    * @return The child Element
    * @throws MalformedXmlException if no child or more than one child present.
    */
   public static Element getConfigUniqueDirectChild(Element parent, String tag) throws MalformedXmlException {
      return XmlUtil.getUniqueDirectChild(parent, SOAPUI_CONFIG_NS, tag);
   }

   /**
    * Check if parent has at least one direct child having namespace and tag.
    * @param parent The parent of children to find
    * @param tag    The tag of children
    * @return true if at least one child is present, false otherwise
    */
   public static boolean hasConfigDirectChild(Element parent, String tag) {
      return XmlUtil.hasDirectChild(parent, SOAPUI_CONFIG_NS, tag);
   }


}
