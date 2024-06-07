package io.github.microcks.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.microcks.domain.Service;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;


class ResourceUtilTest {

  @Test
  void replaceTemplatesInSpecStream() throws Exception {
    // Arrange
    Service service = new Service();
    service.setName("TestService");
    service.setVersion("1.0");

    String resource = "TestResource";

    ObjectMapper mapper = new ObjectMapper();
    JsonNode referenceSchema = mapper.readTree("{\"type\":\"string\"}");

    String referencePayload = "{\"data\":\"TestPayload\"}";

    String template = "{service} {version} {resource} {resourceSchema} {reference}";
    InputStream stream = new ByteArrayInputStream(template.getBytes());

    // Act
    String result = ResourceUtil.replaceTemplatesInSpecStream(stream, service, resource, referenceSchema, referencePayload);

    // Assert
    String expected = """
      TestService 1.0 TestResource type: string
             {"data":"TestPayload"}
      """;
    assertEquals(expected, result);
  }
}
