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
package io.github.microcks.util.el.function;

import io.github.microcks.util.el.EvaluationContext;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Dynamic invocation of any Datafaker provider/method through a single EL function.
 * <p>
 * Usage examples:
 * <ul>
 * <li>${faker(internet.domainName)}</li>
 * <li>${faker(address.city)}</li>
 * <li>${faker(book.title)}</li>
 * <li>${faker(number.numberBetween,10,42)}</li>
 * </ul>
 * The first argument is a dot separated path of provider + final method to invoke starting from {@link Faker} root.
 * Additional arguments are passed as Strings to the final method if parameter types accept them (String/CharSequence or
 * primitive wrapper types with a simple String constructor / parse method). When conversion isn't possible, the no-arg
 * overload is attempted.
 * <p>
 * Any error during reflective lookup simply results in an empty String (and a debug log) so that template evaluation
 * keeps going.
 */
public class DynamicFakerELFunction extends FakerELFunction {

   private static final Logger log = LoggerFactory.getLogger(DynamicFakerELFunction.class);

   @Override
   public String evaluate(EvaluationContext evaluationContext, String... args) {
      if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
         return ""; // Nothing to do.
      }

      String path = args[0].trim();
      String[] segments = path.split("\\.");
      if (segments.length == 0) {
         return "";
      }

      Faker fakerOrigin = retrieveFaker(evaluationContext);

      try {
         // Traverse intermediate provider chain (all except last segment) via helper.
         var provider = traverseProviderChain(fakerOrigin, segments);
         if (provider == null) {
            return "";
         }
         String terminal = segments[segments.length - 1];
         Object result;
         // Remaining args (if any) apply to terminal method.
         String[] callArgs = (args.length > 1) ? sliceArgs(args) : new String[0];
         if (callArgs.length == 0) {
            result = invokeNoArg(provider, terminal);
         } else {
            result = invokeWithArgs(provider, terminal, callArgs);
            if (result == null) { // fallback to no-arg
               result = invokeNoArg(provider, terminal);
            }
         }
         return result == null ? "" : result.toString();
      } catch (Exception e) {
         log.debug("Dynamic faker evaluation failed for path '{}'", path, e);
         return "";
      }
   }

   private Object invokeNoArg(Object target, String methodName) {
      try {
         Method m = target.getClass().getMethod(methodName);
         return m.invoke(target);
      } catch (NoSuchMethodException e) {
         // Try any public method with matching name and no params (iterate methods because getMethod may fail with inheritance edge cases)
         for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
               try {
                  return m.invoke(target);
               } catch (IllegalAccessException | InvocationTargetException ex) {
                  /* ignore */ }
            }
         }
         log.debug("No no-arg method '{}' on {}", methodName, target.getClass().getName());
         return null;
      } catch (InvocationTargetException | IllegalAccessException e) {
         log.debug("Invocation error on method '{}' of {}", methodName, target.getClass().getName(), e);
         return null;
      }
   }

   private Object invokeWithArgs(Object target, String methodName, String[] stringArgs) {
      Method[] candidates = target.getClass().getMethods();
      for (Method m : candidates) {
         if (!m.getName().equals(methodName) || m.getParameterCount() != stringArgs.length) {
            continue;
         }
         Class<?>[] paramTypes = m.getParameterTypes();
         Object[] converted = new Object[paramTypes.length];
         boolean convertible = true;
         for (int i = 0; i < paramTypes.length; i++) {
            Object convertedValue = convertString(stringArgs[i], paramTypes[i]);
            if (convertedValue == null && stringArgs[i] != null) { // cannot convert
               convertible = false;
               break;
            }
            converted[i] = convertedValue;
         }
         if (!convertible) {
            continue; // try next overload
         }
         try {
            return m.invoke(target, converted);
         } catch (IllegalAccessException | InvocationTargetException e) {
            log.debug("Failed invoking '{}' with args on {}", methodName, target.getClass().getName(), e);
            return null;
         }
      }
      return null; // none matched
   }

   private Object convertString(String value, Class<?> targetType) {
      if (targetType == String.class || targetType == CharSequence.class || targetType == Object.class) {
         return value;
      }
      if (Number.class.isAssignableFrom(targetType)) {
         try {
            if (targetType == Integer.class)
               return Integer.valueOf(value);
            if (targetType == Long.class)
               return Long.valueOf(value);
            if (targetType == Double.class)
               return Double.valueOf(value);
            if (targetType == Float.class)
               return Float.valueOf(value);
            if (targetType == Short.class)
               return Short.valueOf(value);
            if (targetType == Byte.class)
               return Byte.valueOf(value);
         } catch (NumberFormatException e) {
            return null;
         }
      }
      if (targetType == Boolean.class || targetType == boolean.class) {
         return Boolean.valueOf(value);
      }
      // Primitive numeric types
      try {
         if (targetType == int.class)
            return Integer.parseInt(value);
         if (targetType == long.class)
            return Long.parseLong(value);
         if (targetType == double.class)
            return Double.parseDouble(value);
         if (targetType == float.class)
            return Float.parseFloat(value);
         if (targetType == short.class)
            return Short.parseShort(value);
         if (targetType == byte.class)
            return Byte.parseByte(value);
      } catch (NumberFormatException e) {
         return null;
      }
      return null; // unsupported target type
   }

   private String[] sliceArgs(String[] args) {
      String[] result = new String[args.length - 1];
      System.arraycopy(args, 1, result, 0, result.length);
      return result;
   }

   /**
    * Traverse the provider chain (all segments except the last) starting from the Faker root object.
    *
    * @param root     The initial faker root or current provider object
    * @param segments The full path segments (last one is terminal method) ["provider1","provider2",...,"method"]
    * @return The last intermediate provider object just before invoking terminal method or null if traversal failed
    */
   private Object traverseProviderChain(Faker root, String[] segments) {
      Object lastProvider = root;
      for (int i = 0; i < segments.length - 1; i++) {
         lastProvider = invokeNoArg(lastProvider, segments[i]);
         if (lastProvider == null) {
            return null;
         }
      }
      return lastProvider;
   }
}
