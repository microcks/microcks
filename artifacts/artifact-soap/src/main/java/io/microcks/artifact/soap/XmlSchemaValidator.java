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

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;

/**
 * Helper class for easy access to a Xml Schema validation. Validators embed an error handler and use a specific
 * LSResourceResolver allowing for resolving relative dependencies from provided XmlSchemas.
 * @author laurent
 */
public class XmlSchemaValidator {

   private XmlSchemaValidator() {
      // Hide the implicit default constructor.
   }

   /**
    * Validation the Xml string with provided stream on its Xml schema.
    * @param schemaStream A stream on reference Xml Schema
    * @param xmlString    String representation of Xml to validate
    * @return A list of validation errors that mey be empty if validation is ok
    * @throws Exception if validator cannot be initialized (in case of malformed schema stream)
    */
   public static List<String> validateXml(InputStream schemaStream, String xmlString) throws Exception {
      return validateXml(schemaStream, xmlString, null);
   }

   /**
    * Validation the Xml string with provided stream on its Xml schema. A resource URL can be provided to resolved
    * relative includes found into Xml Schema stream.
    * @param schemaStream    A stream on reference Xml Schema
    * @param xmlString       String representation of Xml to validate
    * @param baseResourceUrl Base resource URL for resolving relative dependencies
    * @return A list of validation errors that mey be empty if validation is ok
    * @throws Exception if validator cannot be initialized (in case of malformed schema stream)
    */
   public static List<String> validateXml(InputStream schemaStream, String xmlString, String baseResourceUrl)
         throws Exception {
      SchemaFactory factory = SchemaFactory.newDefaultInstance();
      factory.setResourceResolver(new XmlSchemaURLResolver(baseResourceUrl));

      Source schemaFile = new StreamSource(schemaStream);
      Schema schema = factory.newSchema(schemaFile);
      Validator validator = schema.newValidator();

      XmlErrorHandler errorHandler = new XmlErrorHandler();
      validator.setErrorHandler(errorHandler);

      validator.validate(new StreamSource(new StringReader(xmlString)));

      return errorHandler.getExceptions().stream().map(e -> "line " + e.getLineNumber() + ": " + e.getMessage())
            .toList();
   }
}
