package io.github.microcks.util;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonSchemaValidatorNetworkntTest {

  @Test
  void testValidateJsonSuccess() {
    boolean valid = false;
    String schemaText = null;
    String jsonText = "{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}";

    try {
      // Load schema from file.
      schemaText = FileUtils
        .readFileToString(new File("target/test-classes/io/github/microcks/util/car-schema.json"));
      // Validate Json according schema.
      valid = JsonSchemaValidatorNetworknt.isJsonValid(schemaText, jsonText);
    } catch (Exception e) {
      fail("Exception should not be thrown");
    }

    // Assert Json object is valid.
    assertTrue(valid);
  }

  @Test
  void testValidateJsonFailure() {
    boolean valid = true;
    String schemaText = null;
    String jsonText = "{\"id\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}";

    try {
      // Load schema from file.
      schemaText = FileUtils
        .readFileToString(new File("target/test-classes/io/github/microcks/util/car-schema.json"));
      // Validate Json according schema.
      valid = JsonSchemaValidatorNetworknt.isJsonValid(schemaText, jsonText);
    } catch (Exception e) {
      fail("Exception should not be thrown");
    }

    // Assert Json object is not valid.
    assertFalse(valid);

    // Now revalidate and check validation messages.
    List<String> errors = null;
    try {
      errors = JsonSchemaValidatorNetworknt.validateJson(schemaText, jsonText);
    } catch (Exception e) {
      fail("Exception should not be thrown");
    }
    assertEquals(1, errors.size());
    assertEquals("object has missing required properties ([\"name\"])", errors.get(0));
  }

  @Test
  void testValidateJsonUnknownNodeFailure() {
    boolean valid = true;
    String schemaText = null;
    String jsonText = "{\"name\": \"307\", " + "\"model\": \"Peugeot 307\", \"year\": 2003, \"energy\": \"GO\"}";

    try {
      // Load schema from file.
      schemaText = FileUtils
        .readFileToString(new File("target/test-classes/io/github/microcks/util/car-schema-no-addon.json"));
      // Validate Json according schema.
      valid = JsonSchemaValidatorNetworknt.isJsonValid(schemaText, jsonText);
    } catch (Exception e) {
      fail("Exception should not be thrown");
    }

    // Assert Json object is not valid.
    assertFalse(valid);

    // Now revalidate and check validation messages.
    List<String> errors = null;
    try {
      errors = JsonSchemaValidatorNetworknt.validateJson(schemaText, jsonText);
    } catch (Exception e) {
      fail("Exception should not be thrown");
    }
    assertEquals(1, errors.size());
    assertEquals("object instance has properties which are not allowed by the schema: [\"energy\"]", errors.get(0));
  }

}
