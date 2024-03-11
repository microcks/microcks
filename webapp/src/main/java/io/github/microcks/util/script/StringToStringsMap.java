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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A minimalist implementation of com.eviware.soapui.support.types.StringToStringsMap to ensure a compatibility layer
 * withe SoapUI scripting.
 * @author laurent
 */
public class StringToStringsMap extends HashMap<String, List<String>> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(StringToStringsMap.class);

   private boolean equalsOnThis;

   public StringToStringsMap() {
   }

   public List<String> get(String key, List<String> defaultValue) {
      List<String> value = this.get(key);
      return value == null ? defaultValue : value;
   }

   public boolean hasValues(String key) {
      return this.containsKey(key) && ((List) this.get(key)).size() > 0;
   }

   public void add(String key, boolean value) {
      this.add(key, Boolean.toString(value));
   }

   public void add(String key, String string) {
      if (!this.containsKey(key)) {
         this.put(key, new ArrayList<String>());
      }

      this.get(key).add(string);
   }

   public static StringToStringsMap fromHttpHeader(String value) {
      StringToStringsMap result = new StringToStringsMap();

      for (int ix = value.indexOf(59); ix > 0; ix = value.indexOf(59)) {
         extractNVPair(value.substring(0, ix), result);
         value = value.substring(ix + 1);
      }

      if (value.length() > 2) {
         extractNVPair(value, result);
      }

      return result;
   }

   private static void extractNVPair(String value, StringToStringsMap result) {
      int ix = value.indexOf(61);
      if (ix != -1) {
         String str = value.substring(ix + 1).trim();
         if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
         }

         result.add(value.substring(0, ix).trim(), str);
      }

   }

   public void setEqualsOnThis(boolean equalsOnThis) {
      this.equalsOnThis = equalsOnThis;
   }

   public boolean equals(Object o) {
      return this.equalsOnThis ? this == o : super.equals(o);
   }

   public String[] getKeys() {
      return (String[]) this.keySet().toArray(new String[this.size()]);
   }

   public boolean containsKeyIgnoreCase(String string) {
      Iterator var2 = this.keySet().iterator();

      String key;
      do {
         if (!var2.hasNext()) {
            return false;
         }

         key = (String) var2.next();
      } while (!key.equalsIgnoreCase(string));

      return true;
   }

   public void put(String name, String value) {
      this.add(name, value);
   }

   public String get(String key, String defaultValue) {
      List<String> value = this.get(key);
      return value != null && value.size() != 0 ? value.get(0) : defaultValue;
   }

   public String getCaseInsensitive(String key, String defaultValue) {
      Iterator var3 = this.entrySet().iterator();

      Map.Entry stringListEntry;
      do {
         if (!var3.hasNext()) {
            return defaultValue;
         }

         stringListEntry = (Map.Entry) var3.next();
      } while (!key.equalsIgnoreCase((String) stringListEntry.getKey())
            || ((List) stringListEntry.getValue()).isEmpty());

      return (String) ((List) stringListEntry.getValue()).get(0);
   }

   public void replace(String key, String oldValue, String value) {
      List<String> values = this.get(key);
      if (values != null) {
         int ix = values.indexOf(oldValue);
         if (ix >= 0) {
            values.set(ix, value);
         }

      }
   }

   public void remove(String key, String data) {
      List<String> values = this.get(key);
      if (values != null) {
         values.remove(data);
      }
   }

   public int valueCount() {
      int result = 0;

      String key;
      for (Iterator var2 = this.keySet().iterator(); var2.hasNext(); result += ((List) this.get(key)).size()) {
         key = (String) var2.next();
      }

      return result;
   }

   public String toString() {
      StringBuilder result = new StringBuilder();
      Iterator var2 = this.keySet().iterator();

      while (var2.hasNext()) {
         String key = (String) var2.next();
         Iterator var4 = ((List) this.get(key)).iterator();

         while (var4.hasNext()) {
            String value = (String) var4.next();
            result.append(key).append(" : ").append(value).append("\r\n");
         }
      }

      return result.toString();
   }
}
