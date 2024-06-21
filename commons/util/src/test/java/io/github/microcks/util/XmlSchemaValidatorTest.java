package io.github.microcks.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.xml.sax.SAXParseException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmlSchemaValidatorTest {

  @Test
  public void testValidXmlAgainstValidSchema() throws Exception {
    InputStream schemaStream = new FileInputStream("target/test-classes/io/github/microcks/util/valid-schema.xsd");
    String validXml = """
                <note>
                    <to>Tove</to>
                    <from>Jani</from>
                    <heading>Reminder</heading>
                    <body>Don't forget me this weekend!</body>
                </note>
                """;

    List<String> errors = XmlSchemaValidator.validateXml(schemaStream, validXml);
    assertTrue(errors.isEmpty(), "Expected no validation errors, but got: " + errors);
  }

  @Test
  public void testValidXmlAgainstInvalidSchema() throws Exception {
    InputStream schemaStream = new FileInputStream("target/test-classes/io/github/microcks/util/invalid-schema.xsd");
    String validXml = """
                <note>
                    <to>Tove</to>
                    <from>Jani</from>
                    <heading>Reminder</heading>
                    <body>Don't forget me this weekend!</body>
                </note>
                """;

    Executable validationExecutable = () -> XmlSchemaValidator.validateXml(schemaStream, validXml);
    assertThrows(SAXParseException.class, validationExecutable, "Expected SAXParseException due to schema mismatch.");
  }
}

