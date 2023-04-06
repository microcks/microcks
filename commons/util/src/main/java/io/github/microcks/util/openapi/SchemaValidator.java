package io.github.microcks.util.openapi;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class SchemaValidator {
    public abstract List<String> validate(JsonNode schemaNode, JsonNode jsonNode);
}
