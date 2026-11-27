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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMapTest {

   @Test
   void putSchemaEntry() {
      SchemaMap schemaMap = new SchemaMap();
      schemaMap.putSchemaEntry("path1", "schema1");

      assertTrue(schemaMap.hasSchemaEntry("path1"));
      assertEquals("schema1", schemaMap.getSchemaEntry("path1"));
   }

   @Test
   void hasSchemaEntry() {
      SchemaMap schemaMap = new SchemaMap();
      schemaMap.putSchemaEntry("path1", "schema1");

      assertTrue(schemaMap.hasSchemaEntry("path1"));
      assertFalse(schemaMap.hasSchemaEntry("path2"));
   }

   @Test
   void getSchemaEntry() {
      SchemaMap schemaMap = new SchemaMap();
      schemaMap.putSchemaEntry("path1", "schema1");

      assertEquals("schema1", schemaMap.getSchemaEntry("path1"));
      assertNull(schemaMap.getSchemaEntry("path2"));
   }
}
