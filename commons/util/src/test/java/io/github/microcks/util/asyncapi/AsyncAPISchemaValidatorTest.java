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
package io.github.microcks.util.asyncapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.microcks.util.SchemaMap;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * This is a test case for AsyncAPISchemaValidator utility.
 * @author laurent
 */
public class AsyncAPISchemaValidatorTest {

   @Test
   public void testValidateJsonSuccess() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.json"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   public void testValidateJsonSuccessFromYaml() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.yaml"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   public void testValidateJsonFailure() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.json"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);
   }

   @Test
   public void testValidateJsonFailureFromYaml() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.yaml"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);
   }

   @Test
   public void testFullProcedureFromAsyncAPIResource() {
      String asyncAPIText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIResourceWithNumberFormats() {
      String asyncAPIText = null;
      String jsonText = "{\"displayName\": \"Laurent Broudoux\", \"age\": 43, \"size\": 1.8, \"exp\": 1234567891011, \"rewards\": 12345.67}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/account-service-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIResourceWithNumberFormatsWithRef() {
      String asyncAPIText = null;
      String jsonText = "{\"displayName\": \"Laurent Broudoux\", \"age\": 43, \"size\": 1.8, \"exp\": { \"level\": 1234567891011 }, \"rewards\": 12345.67}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/account-service-ref-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIResourceWithNumberFormatsWithRefRef() {
      String asyncAPIText = null;
      String jsonText = "{\"displayName\": \"Laurent Broudoux\", \"age\": 43, \"size\": 1.8, \"exp\": { \"level\": 1234567891011 }, \"rewards\": 12345.67}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/account-service-ref-ref-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIResourceNulls() {
      String asyncAPIText = null;
      String jsonText = "{\n" + "              \"throwable\": null,\n" + "              \"person\": {\n"
            + "                \"taille\": 110,\n" + "                \"nom\": \"Bennour\",\n"
            + "                \"prenom\": \"Hassen\",\n"
            + "                \"dateNaissance\": \"2000-08-24T14:15:22Z\"\n" + "              }\n" + "            }";
      String errorJsonText = "{\n" + "              \"throwable\": {\n"
            + "                \"detailMessage\": \"Exception message\",\n"
            + "                \"clazz\": \"org.acme.MyProducer\"\n" + "                },\n"
            + "              \"person\": null" + "            }";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;
      JsonNode errorContentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(new File(
               "target/test-classes/io/github/microcks/util/asyncapi/spring-cloud-stream-asyncapi-nulls.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
         errorContentNode = AsyncAPISchemaValidator.getJsonNode(errorJsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of domaineA.service1.replier.v1.0.0 subscribe channel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/domaineA.service1.replier.v1.0.0/subscribe/message");
      assertTrue(errors.isEmpty());

      errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, errorContentNode,
            "/channels/domaineA.service1.replier.v1.0.0/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIResourceFailure() {
      String asyncAPIText = null;
      String jsonText = "{\"id\": \"123456\", \"name\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message");
      assertFalse(errors.isEmpty());

      assertEquals(2, errors.size());
      // First error is because payload does not have any ref to components.
      assertEquals("the following keywords are unknown and will be ignored: [components]", errors.get(0));
      assertEquals("object instance has properties which are not allowed by the schema: [\"id\",\"name\"]",
            errors.get(1));
   }

   @Test
   public void testFullProcedureFromAsyncAPIWithRefsResource() {
      String asyncAPIText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-out-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe channel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIWithDeepRefsResource() {
      String asyncAPIText = null;
      String jsonText = "{\"streetlightId\":\"dev0\", \"lumens\":1000, \"sentAt\":\"2020-11-20T21:46:38Z\"}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/streetlights-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception should not be thrown");
      }

      // Validate the content of smartylighting/streetlights/event/lighting/measured subscribe channel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/smartylighting~1streetlights~1event~1lighting~1measured/subscribe/message");

      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIWithDeepRefsResourceFailure() {
      String asyncAPIText = null;
      String jsonText = "{\"streetlightId\":\"dev0\", \"location\":\"47.8509682604982, 0.11136576784773598\", \"sentAt\":\"2020-11-20T21:46:38Z\"}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/streetlights-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception should not be thrown");
      }

      // Validate the content of smartylighting/streetlights/event/lighting/measured subscribe channel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/smartylighting~1streetlights~1event~1lighting~1measured/subscribe/message");

      assertFalse(errors.isEmpty());
      assertEquals(3, errors.size());
      // First error is because payload does not have any ref to components.
      assertEquals("the following keywords are unknown and will be ignored: [components]", errors.get(0));
      assertEquals("object instance has properties which are not allowed by the schema: [\"location\"]", errors.get(1));
      assertEquals("object has missing required properties ([\"lumens\"])", errors.get(2));
   }

   @Test
   public void testFullProcedureFromAsyncAPIWithOneOf21() {
      String asyncAPIText = null;
      String jsonTextAlt1 = "{\"displayName\":\"Alice\"}";
      String jsonTextAlt2 = "{\"email\":\"bob@example.com\"}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode1 = null;
      JsonNode contentNode2 = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/account-service-asyncapi-oneof-2.1.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode1 = AsyncAPISchemaValidator.getJsonNode(jsonTextAlt1);
         contentNode2 = AsyncAPISchemaValidator.getJsonNode(jsonTextAlt2);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe channel with 1st content.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode1,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());

      // Validate the content of user/signedup subscribe channel with 2nd content.
      errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode2,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIWithOneOf23() {
      String asyncAPIText = null;
      String jsonTextAlt1 = "{\"displayName\":\"Alice\"}";
      String jsonTextAlt2 = "{\"email\":\"bob@example.com\"}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode1 = null;
      JsonNode contentNode2 = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/account-service-asyncapi-oneof-2.3.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode1 = AsyncAPISchemaValidator.getJsonNode(jsonTextAlt1);
         contentNode2 = AsyncAPISchemaValidator.getJsonNode(jsonTextAlt2);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe channel with 1st content.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode1,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());

      // Validate the content of user/signedup subscribe channel with 2nd content.
      errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode2,
            "/channels/user~1signedup/subscribe/message");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPI3() {
      String asyncAPIText = null;
      String jsonText = """
               {"fullName": "Laurent Broudoux", "email": "laurent@microcks.io", "age": 45}
            """;
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of publishUserSignedUps operation with content.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/operations/publishUserSignedUps/messages");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPI3WithOneOf() {
      String asyncAPIText = null;
      String jsonTextAlt1 = """
               {"fullName": "Laurent Broudoux", "email": "laurent@microcks.io", "age": 45}
            """;
      String jsonTextAlt2 = """
               {"id": "706a1af6-6a65-4b2a-b350-ece4ea4f7929"}
            """;
      JsonNode asyncAPISpec = null;
      JsonNode contentNode1 = null;
      JsonNode contentNode2 = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-oneof-3.0.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode1 = AsyncAPISchemaValidator.getJsonNode(jsonTextAlt1);
         contentNode2 = AsyncAPISchemaValidator.getJsonNode(jsonTextAlt2);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of publishUserSignedUpOut operation with alternative contents.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode1,
            "/operations/publishUserSignedUpOut/messages");
      assertTrue(errors.isEmpty());

      errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode2,
            "/operations/publishUserSignedUpOut/messages");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIWithExternalRelativeReference() {
      String asyncAPIText = null;
      String jsonText = "{\"fullName\":\"Laurent Broudoux\", \"email\":\"laurent@acme.com\", \"age\": 44}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-json-ref-asyncapi.yaml"));
         // Extract JSON nodes using AsyncAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message",
            "https://raw.githubusercontent.com/microcks/microcks/1.7.x/commons/util/src/test/resources/io/github/microcks/util/asyncapi/");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testValidateAvroSuccessFromAsyncAPIResource() {
      String asyncAPIText = null;
      JsonNode asyncAPISpec = null;
      Schema avroSchema = null;
      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-asyncapi.yaml"));
         // Extract JSON node using AsyncAPISchemaValidator method.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);

         // Load schema from file.
         avroSchema = new Schema.Parser()
               .parse(new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup.avsc"));

         GenericRecord record = new GenericData.Record(avroSchema);
         record.put("fullName", "Laurent Broudoux");
         record.put("email", "laurent@microcks.io");
         record.put("age", 42);

         // Validate the content of user/signedup subscribe chanel.
         List<String> errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, record,
               "/channels/user~1signedup/subscribe/message", null);
         assertTrue(errors.isEmpty());
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testValidateAvroFailureFromAsyncAPIResource() {
      String asyncAPIText = null;
      JsonNode asyncAPISpec = null;
      Schema avroSchema = null;
      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-asyncapi.yaml"));
         // Extract JSON node using AsyncAPISchemaValidator method.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);

         // Load schema from file.
         avroSchema = new Schema.Parser()
               .parse(new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-bad.avsc"));

         GenericRecord record = new GenericData.Record(avroSchema);
         record.put("name", "Laurent");
         record.put("email", "laurent@microcks.io");
         record.put("age", 42);

         // Validate the content of user/signedup subscribe chanel.
         List<String> errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, record,
               "/channels/user~1signedup/subscribe/message", null);
         assertFalse(errors.isEmpty());
         assertEquals(1, errors.size());
         assertEquals("Required field fullName cannot be found in record", errors.get(0));
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testValidateAvroSuccessFromAsyncAPIWithRefsResource() {
      String asyncAPIText = null;
      JsonNode asyncAPISpec = null;
      Schema avroSchema = null;

      SchemaMap schemaMap = new SchemaMap();
      schemaMap.putSchemaEntry("./user-signedup.avsc",
            "{\"namespace\": \"microcks.avro\",\n" + " \"type\": \"record\",\n" + " \"name\": \"User\",\n"
                  + " \"fields\": [\n" + "     {\"name\": \"fullName\", \"type\": \"string\"},\n"
                  + "     {\"name\": \"email\",  \"type\": \"string\"},\n"
                  + "     {\"name\": \"age\", \"type\": \"int\"}\n" + " ]\n" + "}");

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-ref-asyncapi.yaml"));
         // Extract JSON node using AsyncAPISchemaValidator method.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);

         // Load schema from file.
         avroSchema = new Schema.Parser()
               .parse(new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup.avsc"));

         GenericRecord record = new GenericData.Record(avroSchema);
         record.put("fullName", "Laurent Broudoux");
         record.put("email", "laurent@microcks.io");
         record.put("age", 42);

         // Validate the content of user/signedup subscribe chanel.
         List<String> errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, record,
               "/channels/user~1signedup/subscribe/message", schemaMap);
         assertTrue(errors.isEmpty());
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testValidateAvroFailureFromAsyncAPIWithRefsResource() {
      String asyncAPIText = null;
      JsonNode asyncAPISpec = null;
      Schema avroSchema = null;

      SchemaMap schemaMap = new SchemaMap();
      schemaMap.putSchemaEntry("./user-signedup.avsc",
            "{\"namespace\": \"microcks.avro\",\n" + " \"type\": \"record\",\n" + " \"name\": \"User\",\n"
                  + " \"fields\": [\n" + "     {\"name\": \"fullName\", \"type\": \"string\"},\n"
                  + "     {\"name\": \"email\",  \"type\": \"string\"},\n"
                  + "     {\"name\": \"age\", \"type\": \"int\"}\n" + " ]\n" + "}");

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-ref-asyncapi.yaml"));
         // Extract JSON node using AsyncAPISchemaValidator method.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);

         // Load schema from file.
         avroSchema = new Schema.Parser()
               .parse(new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-bad.avsc"));

         GenericRecord record = new GenericData.Record(avroSchema);
         record.put("name", "Laurent");
         record.put("email", "laurent@microcks.io");
         record.put("age", 42);

         // Validate the content of user/signedup subscribe chanel.
         List<String> errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, record,
               "/channels/user~1signedup/subscribe/message", schemaMap);
         assertFalse(errors.isEmpty());
         assertEquals(1, errors.size());
         assertEquals("Required field fullName cannot be found in record", errors.get(0));
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testValidateAvroSuccessFromAsyncAPIWithOneOf23() {
      String asyncAPIText = null;
      JsonNode asyncAPISpec = null;

      Schema signedupSchema = SchemaBuilder.record("SignupUser").fields().requiredString("displayName").endRecord();
      Schema loginSchema = SchemaBuilder.record("LoginUser").fields().requiredString("email").endRecord();

      SchemaMap schemaMap = new SchemaMap();

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File(
                     "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-asyncapi-oneof-2.3.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON node using AsyncAPISchemaValidator method.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);

         // Check with first alternative among oneOfs.
         GenericRecord signedupRecord = new GenericData.Record(signedupSchema);
         signedupRecord.put("displayName", "Laurent Broudoux");

         // Validate the content of user/signedup subscribe chanel.
         List<String> errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, signedupRecord,
               "/channels/user~1signedup/subscribe/message", schemaMap);
         assertTrue(errors.isEmpty());

         // Check with second alternative among oneOfs.
         GenericRecord loginRecord = new GenericData.Record(loginSchema);
         loginRecord.put("email", "laurent@microcks.io");

         // Validate the content of user/signedup subscribe chanel.
         errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, loginRecord,
               "/channels/user~1signedup/subscribe/message", schemaMap);
         assertTrue(errors.isEmpty());
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testValidateAvroSuccessFromAsyncAPIWithOneOf23AndRefsResources() {
      String asyncAPIText = null;
      JsonNode asyncAPISpec = null;

      Schema signedupSchema = SchemaBuilder.record("SignupUser").fields().requiredString("displayName").endRecord();
      Schema loginSchema = SchemaBuilder.record("LoginUser").fields().requiredString("email").endRecord();

      SchemaMap schemaMap = new SchemaMap();
      schemaMap.putSchemaEntry("./user-signedup-signup.avsc",
            "{\"namespace\": \"microcks.avro\",\n" + " \"type\": \"record\",\n" + " \"name\": \"SignupUser\",\n"
                  + " \"fields\": [\n" + "     {\"name\": \"displayName\", \"type\": \"string\"}\n" + " ]\n" + "}");
      schemaMap.putSchemaEntry("./user-signedup-login.avsc",
            "{\"namespace\": \"microcks.avro\",\n" + " \"type\": \"record\",\n" + " \"name\": \"LoginUser\",\n"
                  + " \"fields\": [\n" + "     {\"name\": \"email\", \"type\": \"string\"}\n" + " ]\n" + "}");

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(new File(
               "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-ref-asyncapi-oneof-2.3.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON node using AsyncAPISchemaValidator method.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);

         // Check with first alternative among oneOfs.
         GenericRecord signedupRecord = new GenericData.Record(signedupSchema);
         signedupRecord.put("displayName", "Laurent Broudoux");

         // Validate the content of user/signedup subscribe chanel.
         List<String> errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, signedupRecord,
               "/channels/user~1signedup/subscribe/message", schemaMap);
         assertTrue(errors.isEmpty());

         // Check with second alternative among oneOfs.
         GenericRecord loginRecord = new GenericData.Record(loginSchema);
         loginRecord.put("email", "laurent@microcks.io");

         // Validate the content of user/signedup subscribe chanel.
         errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, loginRecord,
               "/channels/user~1signedup/subscribe/message", schemaMap);
         assertTrue(errors.isEmpty());
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testValidateAvroSuccessFromAsyncAPI3WithOneOf() {
      String asyncAPIText = null;
      JsonNode asyncAPISpec = null;

      Schema signedupSchema = SchemaBuilder.record("SignupUser").fields().requiredString("displayName").endRecord();
      Schema loginSchema = SchemaBuilder.record("LoginUser").fields().requiredString("email").endRecord();

      SchemaMap schemaMap = new SchemaMap();

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File(
                     "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-avro-asyncapi-oneof-3.0.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON node using AsyncAPISchemaValidator method.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);

         // Check with first alternative among oneOfs.
         GenericRecord signedupRecord = new GenericData.Record(signedupSchema);
         signedupRecord.put("displayName", "Laurent Broudoux");

         // Validate the content of user/signedup subscribe chanel.
         List<String> errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, signedupRecord,
               "/operations/publishUserSignUpLogin/messages", schemaMap);
         assertTrue(errors.isEmpty());

         // Check with second alternative among oneOfs.
         GenericRecord loginRecord = new GenericData.Record(loginSchema);
         loginRecord.put("email", "laurent@microcks.io");

         // Validate the content of user/signedup subscribe chanel.
         errors = AsyncAPISchemaValidator.validateAvroMessage(asyncAPISpec, loginRecord,
               "/operations/publishUserSignUpLogin/messages", schemaMap);
         assertTrue(errors.isEmpty());
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }
}
