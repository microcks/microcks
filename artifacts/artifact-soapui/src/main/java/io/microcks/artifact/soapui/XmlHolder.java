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

import io.github.microcks.util.WritableNamespaceContext;

import javax.xml.xpath.XPathExpressionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A minimalist implementation of com.eviware.soapui.support.XmlHolder to ensure a compatibility layer withe SoapUI
 * scripting.
 * @author laurent
 */
public class XmlHolder implements Map<String, Object> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(XmlHolder.class);

   private Element xmlObject;

   private Map<String, String> declaredNamespaces;

   private XPath xpath;

   /**
    * Build a new XmlHolder from xml string.
    * @param xml String representation of Xml managed by this holder.
    * @throws Exception
    */
   public XmlHolder(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
      factory.setNamespaceAware(true);
      DocumentBuilder documentBuilder = factory.newDocumentBuilder();
      xmlObject = documentBuilder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
      xpath = XPathFactory.newInstance().newXPath();
      updateXPathNamespaces(readXPathNamespaces());
   }

   private void updateXPathNamespaces(Map<String, String> declaredNamespaces) {
      WritableNamespaceContext nsContext = new WritableNamespaceContext();
      for (Entry<String, String> entry : declaredNamespaces.entrySet()) {
         nsContext.addNamespaceURI(entry.getKey(), entry.getValue());
      }
      xpath.setNamespaceContext(nsContext);
   }

   private Map<String, String> readXPathNamespaces() {
      Map<String, String> namespaces = new HashMap<>();
      addNamespacesToMap(xmlObject, namespaces);
      return namespaces;
   }

   private static void addNamespacesToMap(Node node, Map<String, String> namespaces) {
      NamedNodeMap attributes = node.getAttributes();
      if (attributes != null) {
         for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (attribute.getNodeType() == Node.ATTRIBUTE_NODE && attribute.getNamespaceURI() != null) {
               namespaces.put(getLocalPart(attribute.getNodeName()), attribute.getNodeValue());
            }
         }
      }

      NodeList child = node.getChildNodes();
      for (int i = 0; i < child.getLength(); i++) {
         addNamespacesToMap(child.item(i), namespaces);
      }
   }

   private static String getLocalPart(String localPart) {
      localPart = localPart.substring(localPart.indexOf(":") + 1);
      return localPart;
   }

   private XPathExpression compileXPath(String xpathExpression) throws XPathExpressionException {
      if (xpathExpression.trim().startsWith("declare namespace")) {
         return SoapUIXPathBuilder.buildXPathMatcherFromRules(xpathExpression);
      }
      return xpath.compile(xpathExpression);
   }

   /**
    * Get a string representation of managed Xml.
    * @return Xml string
    * @throws Exception if Xml cannot be transformed back to string.
    */
   public String getXml() throws Exception {
      TransformerFactory transformerFactory = TransformerFactory.newDefaultInstance();
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

      Writer out = new StringWriter();
      transformer.transform(new DOMSource(xmlObject), new StreamResult(out));
      return out.toString();
   }

   /**
    * Get the managed namespaces.
    * @return A map of namespaces prefix + URI
    */
   public Map<String, String> getNamespaces() {
      if (declaredNamespaces == null) {
         declaredNamespaces = new HashMap<>();
      }
      return declaredNamespaces;
   }

   /**
    * Declare a new namespace to manage.
    * @param prefix Prefix for the namespace
    * @param uri    URI of this namespqce
    */
   public void declareNamespace(String prefix, String uri) {
      if (declaredNamespaces == null) {
         declaredNamespaces = new HashMap<>();
      }
      declaredNamespaces.put(prefix, uri);
      updateXPathNamespaces(declaredNamespaces);
   }

   /**
    * Get the value of a node given an XPath expression
    * @param xpathExpression XPath expression
    * @return The single value
    * @throws Exception if expression is not correct
    */
   public String getNodeValue(String xpathExpression) throws Exception {
      XPathExpression expression = compileXPath(xpathExpression);
      return expression.evaluate(xmlObject);
   }

   /**
    * Get the values of a many nodes given an XPath expression
    * @param xpathExpression XPath expression
    * @return The values of matching nodes
    * @throws Exception if expression is not correct
    */
   public String[] getNodeValues(String xpathExpression) throws Exception {
      XPathExpression expression = compileXPath(xpathExpression);
      NodeList nodeList = (NodeList) expression.evaluate(xmlObject, XPathConstants.NODESET);
      String[] results = new String[nodeList.getLength()];
      for (int i = 0; i < nodeList.getLength(); i++) {
         Node node = nodeList.item(i);
         results[i] = node.getTextContent();
      }
      return results;
   }

   @Override
   public int size() {
      return 0;
   }

   @Override
   public boolean isEmpty() {
      return false;
   }

   @Override
   public boolean containsKey(Object key) {
      return false;
   }

   @Override
   public boolean containsValue(Object value) {
      return false;
   }

   @Override
   public Object get(Object key) {
      String str = key.toString();
      try {
         if (str.equals("xml") || str.equals("prettyXml")) {
            return getXml();
         }
         if (str.equals("namespaces")) {
            return getNamespaces();
         } else {
            // Assume it's an XPath expression.
            String[] nodeValues = this.getNodeValues(str);
            return nodeValues != null && nodeValues.length == 1 ? nodeValues[0] : nodeValues;
         }
      } catch (Exception e) {
         log.error("Exception while getting key {}", key, e);
      }
      return null;
   }

   @Override
   public Object put(String key, Object value) {
      return null;
   }

   @Override
   public Object remove(Object key) {
      return null;
   }

   @Override
   public void putAll(Map<? extends String, ?> m) {
      for (Entry<? extends String, ?> entry : m.entrySet()) {
         put(entry.getKey(), entry.getValue());
      }
   }

   @Override
   public void clear() {
   }

   @Override
   public Set<String> keySet() {
      return null;
   }

   @Override
   public Collection<Object> values() {
      return null;
   }

   @Override
   public Set<Entry<String, Object>> entrySet() {
      return null;
   }
}
