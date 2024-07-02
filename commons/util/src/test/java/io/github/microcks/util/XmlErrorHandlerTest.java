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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlErrorHandlerTest {

   private XmlErrorHandler errorHandler;

   @BeforeEach
   public void setUp() {
      errorHandler = new XmlErrorHandler();
   }

   @Test
   public void testInitialExceptionsListIsEmpty() {
      List<SAXParseException> exceptions = errorHandler.getExceptions();
      assertTrue(exceptions.isEmpty(), "Exceptions list should be empty initially");
   }

   @Test
   public void testWarningAddsException() throws SAXParseException {
      SAXParseException warningException = new SAXParseException("Warning message", null);
      errorHandler.warning(warningException);

      List<SAXParseException> exceptions = errorHandler.getExceptions();
      assertEquals(1, exceptions.size(), "Exceptions list should contain one warning");
      assertEquals(warningException, exceptions.get(0), "Stored exception should match the warning exception");
   }

   @Test
   public void testErrorAddsException() throws SAXParseException {
      SAXParseException errorException = new SAXParseException("Error message", null);
      errorHandler.error(errorException);

      List<SAXParseException> exceptions = errorHandler.getExceptions();
      assertEquals(1, exceptions.size(), "Exceptions list should contain one error");
      assertEquals(errorException, exceptions.get(0), "Stored exception should match the error exception");
   }

   @Test
   public void testFatalErrorAddsException() throws SAXParseException {
      SAXParseException fatalException = new SAXParseException("Fatal error message", null);
      errorHandler.fatalError(fatalException);

      List<SAXParseException> exceptions = errorHandler.getExceptions();
      assertEquals(1, exceptions.size(), "Exceptions list should contain one fatal error");
      assertEquals(fatalException, exceptions.get(0), "Stored exception should match the fatal error exception");
   }

   @Test
   public void testMultipleExceptionsAdded() throws SAXParseException {
      SAXParseException warningException = new SAXParseException("Warning message", null);
      SAXParseException errorException = new SAXParseException("Error message", null);
      SAXParseException fatalException = new SAXParseException("Fatal error message", null);

      errorHandler.warning(warningException);
      errorHandler.error(errorException);
      errorHandler.fatalError(fatalException);

      List<SAXParseException> exceptions = errorHandler.getExceptions();
      assertEquals(3, exceptions.size(), "Exceptions list should contain three exceptions");
      assertEquals(warningException, exceptions.get(0), "First stored exception should match the warning exception");
      assertEquals(errorException, exceptions.get(1), "Second stored exception should match the error exception");
      assertEquals(fatalException, exceptions.get(2), "Third stored exception should match the fatal error exception");
   }
}
