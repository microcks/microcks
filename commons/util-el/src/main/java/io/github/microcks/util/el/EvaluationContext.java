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

import io.github.microcks.util.el.function.ELFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A context used during evaluation of EL expressions. Can be used to register functions, store input variables or
 * intermediary results.
 * @author laurent
 */
public class EvaluationContext {

   private final Map<String, Object> variables = new ConcurrentHashMap<>();

   /**
    * Put a variable into this context
    * @param name  The name of variable
    * @param value The variable itself
    */
   public void setVariable(String name, Object value) {
      variables.put(name, value);
   }

   /**
    * Put a set of variables into this context
    * @param variables key/value pairs for variables names and their values
    */
   public void setVariables(Map<String, Object> variables) {
      variables.forEach(this::setVariable);
   }

   /**
    * Retrieve a registered variable by its name.
    * @param name The name of variable to look for
    * @return The variable having this name or null if no variable.
    */
   public Object lookupVariable(String name) {
      return variables.get(name);
   }

   /**
    * Register a function using a name and the ELFunction class.
    * @param name     The name of function to register
    * @param function The class representing the function
    * @param <T>      Any implementation of {@code ELFunction} interface
    */
   public <T extends ELFunction> void registerFunction(String name, Class<T> function) {
      this.variables.put(name, function);
   }

   /**
    * Retrieve a registered function by its name.
    * @param name The name of function to look for
    * @param <T>  Any implementation of {@code ELFunction} interface
    * @return The Function class object
    */
   @SuppressWarnings("unchecked")
   public <T extends ELFunction> Class<T> lookupFunction(String name) {
      Object function = variables.get(name);
      if (function instanceof Class<?> functionClazz && ELFunction.class.isAssignableFrom(functionClazz)) {
         return (Class<T>) functionClazz;
      }
      return null;
   }
}
