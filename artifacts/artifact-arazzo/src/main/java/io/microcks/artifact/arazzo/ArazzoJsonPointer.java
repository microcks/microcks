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
package io.microcks.artifact.arazzo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * RFC 6901 JSON pointers over the values Jackson reads: maps, lists and scalars.
 * @author SebastienDegodez
 */
public final class ArazzoJsonPointer {

   private ArazzoJsonPointer() {
   }

   /**
    * @param root    The value to read from
    * @param pointer The JSON pointer, empty for the whole value
    * @param owner   What the value is, to tell where a value is missing
    * @return The value at the pointer
    * @throws ArazzoExpressionException if there is no value at the pointer
    */
   public static Object get(Object root, String pointer, String owner) {
      Object current = root;
      for (String token : tokens(pointer)) {
         current = child(current, token, pointer, owner);
      }
      return current;
   }

   /**
    * Write a value into a payload read by Jackson.
    * @param root    The payload, modified in place
    * @param pointer The JSON pointer of an existing member or item; empty to replace the whole payload
    * @param value   The value to write
    * @return The payload with the value written
    * @throws ArazzoExpressionException if the pointer does not lead to a member or an item
    */
   @SuppressWarnings("unchecked")
   public static Object set(Object root, String pointer, Object value) {
      List<String> tokens = tokens(pointer);
      if (tokens.isEmpty()) {
         return value;
      }
      Object parent = root;
      for (String token : tokens.subList(0, tokens.size() - 1)) {
         parent = child(parent, token, pointer, "the request body");
      }
      String last = tokens.get(tokens.size() - 1);
      if (parent instanceof Map<?, ?> map) {
         ((Map<String, Object>) map).put(last, value);
      } else if (parent instanceof List<?> list) {
         ((List<Object>) list).set(index(list, last, pointer, "the request body"), value);
      } else {
         throw noValue(pointer, "the request body");
      }
      return root;
   }

   private static List<String> tokens(String pointer) {
      if (pointer.isEmpty()) {
         return List.of();
      }
      return Arrays.stream(pointer.substring(1).split("/", -1))
            .map(token -> token.replace("~1", "/").replace("~0", "~")).toList();
   }

   private static Object child(Object node, String token, String pointer, String owner) {
      if (node instanceof Map<?, ?> map && map.containsKey(token)) {
         return map.get(token);
      }
      if (node instanceof List<?> list) {
         return list.get(index(list, token, pointer, owner));
      }
      throw noValue(pointer, owner);
   }

   private static int index(List<?> list, String token, String pointer, String owner) {
      try {
         int index = Integer.parseInt(token);
         if (index >= 0 && index < list.size()) {
            return index;
         }
      } catch (NumberFormatException nfe) {
         // Not an index of the list: there is no value there.
      }
      throw noValue(pointer, owner);
   }

   private static ArazzoExpressionException noValue(String pointer, String owner) {
      return new ArazzoExpressionException("No value at JSON pointer " + pointer + " in " + owner);
   }
}
