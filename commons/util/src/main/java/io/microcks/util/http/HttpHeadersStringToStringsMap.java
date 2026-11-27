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
package io.github.microcks.util.script;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An implementation of com.eviware.soapui.support.types.StringToStringsMap that implements the RFC 7230 regarding
 * header name case-insensitiveness and to ensure a compatibility layer withe SoapUI scripting.
 * @author laurent
 */
public class HttpHeadersStringToStringsMap extends StringToStringsMap {

   public HttpHeadersStringToStringsMap() {
   }

   /**
    * Override of HashMap get() to implement case-insensitive search of key.
    * @param key The Http header name (case-insensitive)
    * @return The value as List of String if nay, or null
    */
   @Override
   public List<String> get(Object key) {
      Iterator<Map.Entry<String, List<String>>> var3 = this.entrySet().iterator();

      Map.Entry<String, List<String>> stringListEntry;
      do {
         if (!var3.hasNext()) {
            return null;
         }

         stringListEntry = var3.next();
      } while (!key.toString().equalsIgnoreCase(stringListEntry.getKey()) || (stringListEntry.getValue()).isEmpty());

      return stringListEntry.getValue();
   }

   /**
    * Override of HashMap getOrDefault() to implement case-insensitive search of key.
    * @param key          The Http header name (case-insensitive)
    * @param defaultValue The default mapping of the key
    * @return The value as List of String if nay, or null
    */
   @Override
   public List<String> getOrDefault(Object key, List<String> defaultValue) {
      List<String> value;
      return (value = this.get(key)) == null ? defaultValue : value;
   }


   public boolean hasValues(String key) {
      return this.containsKeyIgnoreCase(key) && ((List) this.get(key)).size() > 0;
   }

   public void add(String key, String string) {
      List<String> updatedValue = this.getOrDefault(key, new ArrayList<String>());
      updatedValue.add(string);
      this.put(key, updatedValue);
   }

   public String get(String key, String defaultValue) {
      return getCaseInsensitive(key, defaultValue);
   }
}
