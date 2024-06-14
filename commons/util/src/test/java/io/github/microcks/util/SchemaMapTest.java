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
