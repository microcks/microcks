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
   public static SafeLogger getLogger(Class clazz) {
      return new SafeLogger(LoggerFactory.getLogger(clazz));
   }

   /**
    * Is the logger instance enabled for the INFO level?
    * @return True if this Logger is enabled for the INFO level, false otherwise.
    */
   public boolean isInfoEnabled() {
      return log.isInfoEnabled();
   }

   public void info(String message) {
      if (log.isInfoEnabled()) {
         log.info(encode(message));
      }
   }

   public void info(String format, Object arg) {
      if (log.isInfoEnabled()) {
         log.info(encode(format), encode(arg));
      }
   }

   public void info(String format, Object arg1, Object arg2) {
      if (log.isInfoEnabled()) {
         log.info(encode(format), encode(arg1), encode(arg2));
      }
   }

   public void info(String format, Object... args) {
      if (log.isInfoEnabled()) {
         log.info(encode(format), encode(args));
      }
   }

   /**
    * Is the logger instance enabled for the DEBUG level?
    * @return True if this Logger is enabled for the DEBUG level, false otherwise.
    */
   public boolean isDebugEnabled() {
      return log.isDebugEnabled();
   }

   public void debug(String message) {
      if (log.isDebugEnabled()) {
         log.debug(encode(message));
      }
   }

   public void debug(String format, Object arg) {
      if (log.isDebugEnabled()) {
         log.debug(encode(format), encode(arg));
      }
   }

   public void debug(String format, Object arg1, Object arg2) {
      if (log.isDebugEnabled()) {
         log.debug(encode(format), encode(arg1), encode(arg2));
      }
   }

   public void debug(String format, Object... arguments) {
      if (log.isDebugEnabled()) {
         log.debug(encode(format), encode(arguments));
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
