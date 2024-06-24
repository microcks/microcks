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

