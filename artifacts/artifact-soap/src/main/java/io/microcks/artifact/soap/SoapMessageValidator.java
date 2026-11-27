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
package io.github.microcks.util.soap;

import io.github.microcks.util.XmlSchemaValidator;
import io.github.microcks.util.XmlUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for validating Soap messages objects against their WSDL/XSD schemas. Supported versions of Soap are Soap
 * 1.1 and Soap 1.2.
 * @author laurent
 */
public class SoapMessageValidator {


   /** A commons logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapMessageValidator.class);

   private static final String SOAP_ENVELOPE_SCHEMA = "soap-envelope.xsd";

   private static final String SOAP_ENVELOPE_12_SCHEMA = "soap-envelope-12.xsd";

   /** Soap 1.1 envelope public namespace. */
   public static final String SOAP_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";
   /** Soap 1.2 envelope public namespace. */
   public static final String SOAP_ENVELOPE_12_NS = "http://www.w3.org/2003/05/soap-envelope";

   private static final Pattern XML_NS_CAPTURE_PATTERN = Pattern.compile("xmlns:(\\w+)=\"([^\"]*)\"", Pattern.DOTALL);


   private SoapMessageValidator() {
      // Hide the implicit default constructor.
   }

   /**
    * Validate that given message has a well-formed Soap Envelope. This could Soap 1.1 or 1.2 envelope.
    * @param message The Soap message to validate.
    * @return A list of validation error that's empty if everything's ok.
    */
   public static List<String> validateSoapEnvelope(String message) {
      List<String> errors = new ArrayList<>();

      InputStream schemaStream;
      if (message.contains(SOAP_ENVELOPE_12_NS)) {
         schemaStream = SoapMessageValidator.class.getClassLoader().getResourceAsStream(SOAP_ENVELOPE_12_SCHEMA);
      } else if (message.contains(SOAP_ENVELOPE_NS)) {
         schemaStream = SoapMessageValidator.class.getClassLoader().getResourceAsStream(SOAP_ENVELOPE_SCHEMA);
      } else {
         errors.add("Soap envelope does not appear to be valid: unrecognized namespace");
         return errors;
      }

      try {
         errors.addAll(XmlSchemaValidator.validateXml(schemaStream, message));
      } catch (Exception e) {
         log.error("Exception while validating Soap envelope for message: {}", e.getMessage());
         errors.add("Exception while validating Soap envelope for message: " + e.getMessage());
      }

      log.debug("SoapEnvelope validation errors: {}", errors.size());
      return errors;
   }

   /**
    * Validate a complete Soap message: Soap envelope + payload included into Soap body against a WSDL content.
    * @param wsdlContent The reference WSDL as a String
    * @param partQName   The qualified name of the element that is expected into Soap body.
    * @param message     The Soap message as a String
    * @param resourceUrl AN optional resource url where includes in WSDL may be resolved
    * @return A list of validation error that's empty if everything's ok.
    */
   public static List<String> validateSoapMessage(String wsdlContent, QName partQName, String message,
         String resourceUrl) {
      List<String> errors = new ArrayList<>();

      // Start validating envelope.
      errors.addAll(validateSoapEnvelope(message));

      try {
         // First thing is to parse WSDL to extract types schema.
         DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
         factory.setNamespaceAware(true);
         DocumentBuilder documentBuilder = factory.newDocumentBuilder();

         Element wsdlElement = documentBuilder.parse(new InputSource(new StringReader(wsdlContent)))
               .getDocumentElement();
         Element wsdlTypes = XmlUtil.getUniqueDirectChild(wsdlElement, XmlUtil.WSDL_NS, "types");
         Element xsSchema = XmlUtil.getUniqueDirectChild(wsdlTypes, XmlUtil.XML_SCHEMA_NS, "schema");

         // Schema may not contain all the needed xmlns declaration found at the definitions level.
         // We have to report it before extracting a standalone schema.
         NamedNodeMap definitionsAttributes = wsdlElement.getAttributes();
         for (int i = 0; i < definitionsAttributes.getLength(); i++) {
            Node attribute = definitionsAttributes.item(i);
            String name = attribute.getNodeName();
            if (name.startsWith("xmlns:") && !xsSchema.hasAttribute(name)) {
               xsSchema.setAttribute(name, attribute.getNodeValue());
            }
         }

         TransformerFactory transformerFactory = TransformerFactory.newDefaultInstance();
         Transformer transformer = transformerFactory.newTransformer();
         transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
         transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

         Writer out = new StringWriter();
         transformer.transform(new DOMSource(xsSchema), new StreamResult(out));

         String schemaContent = out.toString();

         // Before extracting and validating body, we should capture all namespaces declaration to keep record
         // for later reinject them into body.
         Matcher nsMatcher = XML_NS_CAPTURE_PATTERN.matcher(message);
         Map<String, String> nsToPrefix = new HashMap<>();
         StringBuilder nsBuilder = new StringBuilder();
         while (nsMatcher.find()) {
            nsBuilder.append(" ").append(nsMatcher.group(0));
            nsToPrefix.put(nsMatcher.group(2), nsMatcher.group(1));
         }

         // Then we have to extract body payload from soap envelope. We'll use no regexp for that.
         String body = message;
         int bodyStart = body.indexOf(":Body");
         int bodyEnd = body.lastIndexOf(":Body>");
         body = body.substring(bodyStart + 5, bodyEnd);
         int firstClosingTag = body.indexOf(">");
         int lastClosingTag = body.lastIndexOf("</");
         body = body.substring(firstClosingTag + 1, lastClosingTag).trim();

         // Remove all namespaces declaration from body to avoid duplicates.
         body = body.replaceAll("\s" + XML_NS_CAPTURE_PATTERN.pattern(), "");

         // Then we have to reintegrate namespace declarations in englobing element.
         body = body.substring(0, body.indexOf('>')) + nsBuilder + body.substring(body.indexOf('>'));

         log.debug("Soap message body to validate: {}", body);

         // Check soap message if the expected part.
         String expectedStartTag = "<" + nsToPrefix.get(partQName.getNamespaceURI()) + ":" + partQName.getLocalPart();
         String expectedEndTag = "</" + nsToPrefix.get(partQName.getNamespaceURI()) + ":" + partQName.getLocalPart()
               + ">";
         if (!body.startsWith(expectedStartTag) || !body.endsWith(expectedEndTag)) {
            errors.add("Expecting a " + partQName + " element but got another one");
         }

         // And finally validate everything using that body ;-)
         errors.addAll(XmlSchemaValidator.validateXml(
               new ByteArrayInputStream(schemaContent.getBytes(StandardCharsets.UTF_8)), body, resourceUrl));
      } catch (Exception e) {
         log.error("Exception while validating Soap message: {}", e.getMessage());
         errors.add("Exception while validating Soap message: " + e.getMessage());
      }

      log.debug("SoapMessage validation errors: {}", errors.size());
      return errors;
   }
}
