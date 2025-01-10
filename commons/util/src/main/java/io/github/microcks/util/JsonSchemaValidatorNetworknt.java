package io.github.microcks.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonLoader;
import com.networknt.schema.*;
import com.networknt.schema.serialization.DefaultJsonNodeReader;
import com.networknt.schema.serialization.node.JsonNodeFactoryFactory;
import com.networknt.schema.utils.JsonNodeUtil;
import com.networknt.schema.utils.JsonNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JsonSchemaValidatorNetworknt {

  /** A commons logger for diagnostic messages. */
  private static Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

  public static final String JSON_V4_SCHEMA_IDENTIFIER = "http://json-schema.org/draft-04/schema#";
  public static final String JSON_V7_SCHEMA_IDENTIFIER = "http://json-schema.org/draft-07/schema#";
  public static final String JSON_V12_SCHEMA_IDENTIFIER = "http://json-schema.org/draft/2020-12/schema#";
  public static final String JSON_SCHEMA_IDENTIFIER_ELEMENT = "$schema";

  public static final String JSON_SCHEMA_COMPONENTS_ELEMENT = "components";
  public static final String JSON_SCHEMA_PROPERTIES_ELEMENT = "properties";

  private JsonSchemaValidatorNetworknt(){

  }

  public static boolean isJsonValid(String schemaText, String jsonText) throws IOException {
    return isJsonValid(schemaText, jsonText, null);
  }

  public static boolean isJsonValid(String schemaText, String jsonText, String namespace) throws IOException {
    try {
      List<String> errors = validateJson(schemaText, jsonText, namespace);
      if (!errors.isEmpty()) {
        log.debug("Get validation errors, returning false");
        return false;
      }
    } catch (Exception pe) {
      log.debug("Got processing exception while extracting schema, returning false");
      return false;
    }
    return true;
  }

  public static List<String> validateJson(String schemaText, String jsonText) throws IOException {
    return validateJson(getJsonNode(schemaText), getJsonNode(jsonText), null);
  }

  public static List<String> validateJson(String schemaText, String jsonText, String namespace)
    throws IOException {
    return validateJson(getJsonNode(schemaText), getJsonNode(jsonText), namespace);
  }

  public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) {
    return validateJson(schemaNode, jsonNode, null);
  }

  public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode, String namespace) {
    List<String> errors = new ArrayList<>();

    final JsonSchema jsonSchemaNode = extractJsonSchemaNode(schemaNode, namespace);

    Set<ValidationMessage> messages = jsonSchemaNode.validate(schemaNode);

    if(!messages.isEmpty()) {
      for (ValidationMessage message : messages) {
        errors.add(message.toString());
      }
    }

    // Ask for a deep check to get a full error report.
//    ProcessingReport report = jsonSchemaNode.validate(jsonNode, true);
//    if (!report.isSuccess()) {
//      for (ProcessingMessage processingMessage : report) {
//        errors.add(processingMessage.getMessage());
//      }
//    }
    return errors;
  }

  public static JsonNode getJsonNode(String jsonText) throws IOException {
    return JsonLoader.fromString(jsonText);
  }

  public static com.networknt.schema.JsonSchema getSchemaNode(String schemaText) throws IOException{
    final JsonNode schemaNode = getJsonNode(schemaText);
    return extractJsonSchemaNode(schemaNode, null);
  }

  private static com.networknt.schema.JsonSchema extractJsonSchemaNode(JsonNode jsonNode, String namespace){
    final JsonNode schemaIdentifier = jsonNode.get(JSON_SCHEMA_IDENTIFIER_ELEMENT);
    if (schemaIdentifier == null) {
      ((ObjectNode) jsonNode).put(JSON_SCHEMA_IDENTIFIER_ELEMENT, JSON_V7_SCHEMA_IDENTIFIER);
    }
    final com.networknt.schema.JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    return schemaFactory.getSchema(jsonNode);
//    if (namespace != null) {
//      log.debug("Setting namespace to {} in Json schema loading configuration", namespace);
//      // Set up a loading configuration for provided namespace.
//      final LoadingConfiguration cfg = LoadingConfiguration.newBuilder()
//        .setURITranslatorConfiguration(URITranslatorConfiguration.newBuilder().setNamespace(namespace).freeze())
//        .freeze();
//      factory = JsonSchemaFactory.newBuilder().setLoadingConfiguration(cfg).freeze();
//    } else {
//      factory = JsonSchemaFactory.byDefault();
//    }

//    return factory.getJsonSchema(jsonNode);
  }
}
