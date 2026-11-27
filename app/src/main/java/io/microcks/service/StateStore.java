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

package io.github.microcks.service;

import javax.annotation.Nullable;

/**
 * Simple service that allows storing some state as Key/Value pair.
 * @author laurent
 */
public interface StateStore {

   /**
    * Put a KV into the store.
    * @param key   The unique key
    * @param value The value corresponding to key
    */
   void put(String key, String value);

   /**
    * Put a KV into the store with a specific Time To Live.
    * @param key        The unique key
    * @param value      The value corresponding to key
    * @param secondsTTL The TTL in seconds
    */
   void put(String key, String value, int secondsTTL);

   /**
    * Retrieve a state value using its unique key.
    * @param key The unique key to get value for
    * @return The state value (it may be null)
    */
   @Nullable
   String get(String key);

   /**
    * Delete a key corresponding to this unique key.
    * @param key The unique key to remove value for
    */
   void delete(String key);
}
