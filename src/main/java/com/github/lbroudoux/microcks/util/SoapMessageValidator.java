/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.lbroudoux.microcks.util;

import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlContext;
import com.eviware.soapui.support.xml.XmlUtils;
import org.apache.xmlbeans.SchemaGlobalElement;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlLineNumber;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for validating Soap Messages against their WSDL. Code here is mainly extracted and adapted from
 * SoapUI WsdlValidator class (see https://github.com/SmartBear/soapui/blob/master/soapui/src/main/java/com/eviware/soapui/impl/wsdl/support/wsdl/WsdlValidator.java
 * for more details)
 * @author laurent
 */
public class SoapMessageValidator {

   /** A commons logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapMessageValidator.class);
   
   /**
    * Validate a soap message accordingly to its WSDL and linked XSD resources. The validation is
    * done for a specified message part (maybe be the input, output or fault of an operation).
    * @param partName The name of the part to validate ie. name of the input, output or fault part (ex: sayHello)
    * @param partNamespace The namespace of the part to validate (ex: http://www.mma.fr/test/service)
    * @param message The full soap message as a string
    * @param wsdlUrl The URL where we can resolve service and operation WSDL
    * @param validateMessageBody Should we validate also the body ? If false, only Soap envelope is validated.
    * @return The list of validation failures. If empty, message is valid !
    * @throws org.apache.xmlbeans.XmlException if given message is not a valid Xml document
    */
   public static List<XmlError> validateSoapMessage(String partName, String partNamespace, String message, String wsdlUrl, boolean validateMessageBody) 
         throws XmlException {
      //
      WsdlContext ctx = new WsdlContext(wsdlUrl);
      List<XmlError> errors = new ArrayList<XmlError>();
      ctx.getSoapVersion().validateSoapEnvelope(message, errors);
      
      log.debug("SoapEnvelope validation errors: " + errors.size());
   
      if (validateMessageBody){
         // Create XmlBeans object for the soap message.
         XmlOptions xmlOptions = new XmlOptions();
         xmlOptions.setLoadLineNumbers();
         xmlOptions.setLoadLineNumbers( XmlOptions.LOAD_LINE_NUMBERS_END_ELEMENT );
         XmlObject xml = XmlUtils.createXmlObject(message, xmlOptions);

         // Build the QName string of the part name. Ex: {http://www.mma.fr/test/service}sayHello
         String fullPartName = "{" + partNamespace + "}" + partName;
         
         // Extract the corrsponding part from soap body.
         XmlObject[] paths = xml.selectPath( "declare namespace env='" + ctx.getSoapVersion().getEnvelopeNamespace() + "';" 
               + "declare namespace ns='" + partNamespace + "';" + "$this/env:Envelope/env:Body/ns:" + partName);
            
         SchemaGlobalElement elm;
         try {
            elm = ctx.getSchemaTypeLoader().findElement(QName.valueOf(fullPartName));
         } catch (Exception e) {
            log.error("Exception while loading schema information for " + fullPartName, e);
            throw new XmlException("Exception while loading schema information for " + fullPartName, e);
         }
         
         if ( elm != null ){
            validateMessageBody(ctx, errors, elm.getType(), paths[0]);
      
            // Ensure no other elements in body.
            NodeList children = XmlUtils.getChildElements((Element) paths[0].getDomNode().getParentNode());
            for (int c = 0; c < children.getLength(); c++){
               QName childName = XmlUtils.getQName(children.item(c));
               // Compare child QName to full part QName.
               if (!fullPartName.equals(childName.toString())){
                  XmlCursor cur = paths[0].newCursor();
                  cur.toParent();
                  cur.toChild( childName );
                  errors.add( XmlError.forCursor( "Invalid element [" + childName + "] in SOAP Body", cur ) );
                  cur.dispose();
               }
            }
         }
         log.debug("SoapBody validation errors: " + errors.size());
      } 
      return errors;
   }
   
   /** Helper message for validating the body of a Soap message. */
   private static void validateMessageBody(WsdlContext ctx, List<XmlError> errors, SchemaType type, XmlObject msg) throws XmlException {
      // Need to create new body element of correct type from xml text since we want to retain line-numbers.
      XmlOptions xmlOptions = new XmlOptions();
      xmlOptions.setLoadLineNumbers();
      xmlOptions.setLoadLineNumbers(XmlOptions.LOAD_LINE_NUMBERS_END_ELEMENT);

      XmlCursor cur = msg.newCursor();
      Map<String, String> map = new HashMap<String, String>();

      while (cur.hasNextToken()) {
         if (cur.toNextToken().isNamespace())
            map.put(cur.getName().getLocalPart(), cur.getTextValue());
      }

      xmlOptions.setUseDefaultNamespace();
      xmlOptions.setSaveOuter();

      // Problem: prefixes might get redefined/changed when saving which can cause xsi:type refs to
      // reference wrong/non-existing namespace.. solution would probably be to manually walk through 
      // document and update xsi:type refs with new prefix. The setUseDefaultNamespace() above helps
      // here but is not a definitive fix.

      String xmlText = msg.copy().changeType(type).xmlText(xmlOptions);

      xmlOptions.setLoadAdditionalNamespaces(map);

      XmlObject obj = type.getTypeSystem().parse(xmlText, type, xmlOptions);
      obj = obj.changeType(type);

      // Create internal error list.
      ArrayList<Object> list = new ArrayList<Object>();

      xmlOptions = new XmlOptions();
      xmlOptions.setErrorListener(list);
      xmlOptions.setValidateTreatLaxAsSkip();

      try {
         obj.validate(xmlOptions);
      } catch (Exception e) {
         list.add("Internal Error - see error log for details - [" + e + "]");
      }

      // Transfer errors for "real" line numbers.
      for (int c = 0; c < list.size(); c++) {
         XmlError error = (XmlError) list.get(c);

         if (error instanceof XmlValidationError) {
            XmlValidationError validationError = ((XmlValidationError) error);

            if (ctx.getSoapVersion().shouldIgnore(validationError))
               continue;

            // Ignore cid: related errors
            if (validationError.getErrorCode().equals("base64Binary")
                  || validationError.getErrorCode().equals("hexBinary")) {
               XmlCursor cursor = validationError.getCursorLocation();
               if (cursor.toParent()) {
                  String text = cursor.getTextValue();

                  // Special handling for soapui/MTOM -> add option for disabling?
                  if (text.startsWith("cid:") || text.startsWith("file:")) {
                     // ignore
                     continue;
                  }
               }
            }
         }

         int line = error.getLine() == -1 ? 0 : error.getLine() - 1;
         errors.add(XmlError.forLocation(error.getMessage(),
               error.getSourceName(), getLine(msg) + line, error.getColumn(),
               error.getOffset()));
      }
   }
   
   /** Helper for retrieving the real line of an error. */
   private static int getLine(XmlObject object) {
      List<?> list = new ArrayList<Object>();
      object.newCursor().getAllBookmarkRefs(list);
      for (int c = 0; c < list.size(); c++) {
         if (list.get(c) instanceof XmlLineNumber) {
            return ((XmlLineNumber) list.get(c)).getLine();
         }
      }

      return -1;
   }
}
