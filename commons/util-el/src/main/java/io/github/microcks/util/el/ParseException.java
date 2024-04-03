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
package io.github.microcks.util.el;

/**
 * Represent an exception that occurs during expression parsing.
 * @author laurent
 */
public class ParseException extends RuntimeException {

   protected String expressionString = null;

   protected int position = 0;

   /**
    * Create a new default expression parsing exception.
    * @param message description of the problem that occurred
    */
   public ParseException(String message) {
      super(message);
   }

   /**
    * Create a new expression parsing exception.
    * @param expressionString the expression string that could not be parsed
    * @param position         the position in the expression string where the problem occurred
    * @param message          description of the problem that occurred
    */
   public ParseException(String expressionString, int position, String message) {
      super(message);
      this.expressionString = expressionString;
      this.position = position;
   }
}
