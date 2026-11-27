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

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Builder from creating XPathExpression matcher from SoapUI rules.
 * @author laurent
 */
public class SoapUIXPathBuilder {

   /**
    * Build a XPath expressions matcher from SoapUI Rules.
    * @param rules The string representing the rules.
    * @return An XPathExpression following the given rules
    * @throws XPathExpressionException if something wrong occurs.
    */
   public static XPathExpression buildXPathMatcherFromRules(String rules) throws XPathExpressionException {
      XPath xpath = XPathFactory.newInstance().newXPath();
      WritableNamespaceContext nsContext = new WritableNamespaceContext();

      // Parse SoapUI rules for getting namespaces and expression to evaluate.
      // declare namespace ser='http://www.example.com/test/service';
      // //ser:sayHello/name
      String xpathExpression = null;
      String lines[] = rules.split("\\r?\\n");
      for (String line : lines) {
         line = line.trim();
         if (line.startsWith("declare namespace ")) {
            String prefix = line.substring(18, line.indexOf("="));
            String namespace = line.substring(line.indexOf("=") + 2, line.lastIndexOf("'"));
            nsContext.addNamespaceURI(prefix, namespace);
         } else {
            xpathExpression = line;
         }
      }

      // Set namespace context and compile expression.
      xpath.setNamespaceContext(nsContext);
      return xpath.compile(xpathExpression);
   }
}
