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

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple SAX error handler that accumulates exception within an exceptions list.
 * @author laurent
 */
public class XmlErrorHandler implements ErrorHandler {

   private List<SAXParseException> exceptions;

   public XmlErrorHandler() {
      this.exceptions = new ArrayList<>();
   }

   /**
    * Get attached Xml validation errors.
    * @return A list of SAX parsing exception while line numbers and explanation of validation failures.
    */
   public List<SAXParseException> getExceptions() {
      return exceptions;
   }

   @Override
   public void warning(SAXParseException exception) {
      exceptions.add(exception);
   }

   @Override
   public void error(SAXParseException exception) {
      exceptions.add(exception);
   }

   @Override
   public void fatalError(SAXParseException exception) {
      exceptions.add(exception);
   }
}
