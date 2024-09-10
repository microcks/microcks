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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * An encapsulation of a SL4J logger that provides safe logging methods that encodes user-controller data before
 * actually logging (see Sonar rule javasecurity:S5145). This Logger does not strictly implement the SLF4J Logger
 * interface, but provides a subset of its methods.
 * @author laurent
 */
public class SafeLogger {

   private final Logger log;

   private SafeLogger(Logger log) {
      this.log = log;
   }

   /**
    * Return a logger named corresponding to the class passed as parameter, using the statically bound ILoggerFactory
    * instance.
    * @param clazz the returned logger will be named after clazz
    * @return logger
    */
   public static SafeLogger getLogger(Class<?> clazz) {
      return new SafeLogger(LoggerFactory.getLogger(clazz));
   }

   /**
    * Is the logger instance enabled for the DEBUG level?
    * @return True if this Logger is enabled for the DEBUG level, false otherwise.
    */
   public boolean isDebugEnabled() {
      return log.isDebugEnabled();
   }

   /**
    * Log a message at the DEBUG level.
    * @param message the message string to be logged
    */
   public void debug(String message) {
      if (log.isDebugEnabled()) {
         log.debug(encode(message));
      }
   }

   /**
    * Log a message at the DEBUG level according to the specified format and argument. This form avoids superfluous
    * object creation when the logger is disabled for the DEBUG level.
    * @param format the format string
    * @param arg    the argument
    */
   public void debug(String format, Object arg) {
      if (log.isDebugEnabled()) {
         log.debug(encode(format), encode(arg));
      }
   }

   /**
    * Log a message at the DEBUG level according to the specified format and arguments. This form avoids superfluous
    * object creation when the logger is disabled for the DEBUG level.
    * @param format the format string
    * @param arg1   the first argument
    * @param arg2   the second argument
    */
   public void debug(String format, Object arg1, Object arg2) {
      if (log.isDebugEnabled()) {
         log.debug(encode(format), encode(arg1), encode(arg2));
      }
   }

   /**
    * Log a message at the DEBUG level according to the specified format and arguments. This form avoids superfluous
    * string concatenation when the logger is disabled for the DEBUG level. However, this variant incurs the hidden (and
    * relatively small) cost of creating an Object[] before invoking the method, even if this logger is disabled for
    * DEBUG. The variants taking one and two arguments exist solely in order to avoid this hidden cost.
    * @param format    the format string
    * @param arguments a list of 3 or more arguments
    */
   public void debug(String format, Object... arguments) {
      if (log.isDebugEnabled()) {
         log.debug(encode(format), encode(arguments));
      }
   }

   /**
    * Is the logger instance enabled for the INFO level?
    * @return True if this Logger is enabled for the INFO level, false otherwise.
    */
   public boolean isInfoEnabled() {
      return log.isInfoEnabled();
   }

   /**
    * Log a message at the INFO level.
    * @param message the message string to be logged
    */
   public void info(String message) {
      if (log.isInfoEnabled()) {
         log.info(encode(message));
      }
   }

   /**
    * Log a message at the INFO level according to the specified format and argument. This form avoids superfluous
    * object creation when the logger is disabled for the INFO level.
    * @param format the format string
    * @param arg    the argument
    */
   public void info(String format, Object arg) {
      if (log.isInfoEnabled()) {
         log.info(encode(format), encode(arg));
      }
   }

   /**
    * Log a message at the INFO level according to the specified format and arguments. This form avoids superfluous
    * object creation when the logger is disabled for the INFO level.
    * @param format the format string
    * @param arg1   the first argument
    * @param arg2   the second argument
    */
   public void info(String format, Object arg1, Object arg2) {
      if (log.isInfoEnabled()) {
         log.info(encode(format), encode(arg1), encode(arg2));
      }
   }

   /**
    * Log a message at the INFO level according to the specified format and arguments. This form avoids superfluous
    * string concatenation when the logger is disabled for the INFO level. However, this variant incurs the hidden (and
    * relatively small) cost of creating an Object[] before invoking the method, even if this logger is disabled for
    * INFO. The variants taking one and two arguments exist solely in order to avoid this hidden cost.
    * @param format the format string
    * @param args   a list of 3 or more arguments
    */
   public void info(String format, Object... args) {
      if (log.isInfoEnabled()) {
         log.info(encode(format), encode(args));
      }
   }

   /**
    * Is the logger instance enabled for the WARN level?
    * @return True if this Logger is enabled for the WARN level, false otherwise.
    */
   public boolean isWarnEnabled() {
      return log.isWarnEnabled();
   }

   /**
    * Log an exception (throwable) at the WARN level with an accompanying message.
    * @param message the message string to be logged
    * @param t       the exception (throwable) to log
    */
   public void warn(String message, Throwable t) {
      if (log.isWarnEnabled()) {
         log.warn(encode(message), t);
      }
   }

   /**
    * Is the logger instance enabled for the ERROR level?
    * @return True if this Logger is enabled for the ERROR level, false otherwise.
    */
   public boolean isErrorEnabled() {
      return log.isErrorEnabled();
   }

   /**
    * Log a message at the ERROR level.
    * @param message the message string to be logged
    */
   public void error(String message) {
      if (log.isErrorEnabled()) {
         log.error(encode(message));
      }
   }

   /**
    * Log an exception (throwable) at the ERROR level with an accompanying message.
    * @param message the message accompanying the exception
    * @param t       the exception (throwable) to log
    */
   public void error(String message, Throwable t) {
      if (log.isErrorEnabled()) {
         log.error(encode(message), t);
      }
   }

   /**
    * Log a message at the ERROR level according to the specified format and arguments. This form avoids superfluous
    * object creation when the logger is disabled for the ERROR level.
    * @param format the format string
    * @param arg    the argument
    */
   public void error(String format, Object arg) {
      if (log.isErrorEnabled()) {
         log.error(encode(format), encode(arg));
      }
   }

   /**
    * Log a message at the ERROR level according to the specified format and arguments. This form avoids superfluous
    * object creation when the logger is disabled for the ERROR level.
    * @param format the format string
    * @param arg1   the first argument
    * @param arg2   the second argument
    */
   public void error(String format, Object arg1, Object arg2) {
      if (log.isErrorEnabled()) {
         log.error(encode(format), encode(arg1), encode(arg2));
      }
   }

   private String encode(String message) {
      return message.replaceAll("[\n\r]", "_");
   }

   private Object encode(Object argument) {
      if (argument instanceof String stringArg) {
         return encode(stringArg);
      }
      return argument;
   }

   private Object[] encode(Object... arguments) {
      return Arrays.stream(arguments).sequential().map(this::encode).toArray();
   }
}
