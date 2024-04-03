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

import javax.xml.namespace.NamespaceContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Does not implement reverse operations for now, but not necessary for XPath processing that is our main purpose. Have
 * to check Guava
 * (http://guava-libraries.googlecode.com/svn/tags/release09/javadoc/com/google/common/collect/BiMap.html) for
 * Bidirectional map when looking for this ?
 * @author laurent
 */
public class WritableNamespaceContext implements NamespaceContext {

   private Map<String, String> prefixURIMap = new HashMap<>();

   public void addNamespaceURI(String prefix, String namespaceURI) {
      prefixURIMap.put(prefix, namespaceURI);
   }

   @Override
   public String getNamespaceURI(String prefix) {
      return prefixURIMap.get(prefix);
   }

   @Override
   public String getPrefix(String namespaceURI) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Iterator<String> getPrefixes(String namespaceURI) {
      throw new UnsupportedOperationException();
   }
}
