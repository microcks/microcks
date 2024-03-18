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

import java.util.HashMap;
import java.util.Map;

/**
 * This is a lightweight structure representing a Schema registry snapshot dedicated for being used by message
 * validators.
 * @author laurent
 */
public class SchemaMap {

   /** The internal map backing schemas storage. This is an in-memory map. */
   private Map<String, String> schemaEntries;

   /** Build a new schema map with empty content. */
   public SchemaMap() {
      schemaEntries = new HashMap<>();
   }

   /**
    * Initialize a new schema map with provided content.
    * @param schemaEntries Map of schema entries. Keys are schema paths, Values are schema string representation.
    */
   public SchemaMap(Map<String, String> schemaEntries) {
      this.schemaEntries = schemaEntries;
   }

   /**
    * Put a new schema entry in map.
    * @param schemaPath The path of this new entry.
    * @param content    The string representation of schema content
    */
   public void putSchemaEntry(String schemaPath, String content) {
      schemaEntries.put(schemaPath, content);
   }

   /**
    * Check if we've got an entry for this schema path.
    * @param schemaPath The path of searched entry.
    * @return True if we've got a corresponding entry, false otherwise.
    */
   public boolean hasSchemaEntry(String schemaPath) {
      return schemaEntries.containsKey(schemaPath);
   }

   /**
    * Get schema entry string content.
    * @param schemaPath The path of searched entry.
    * @return The string representation of schema content
    */
   public String getSchemaEntry(String schemaPath) {
      return schemaEntries.get(schemaPath);
   }
}
