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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of {@code Expression} that deals with variable references. Such expression is able to evaluate
 * simple forms like {@code request.body} where {@code request} is provided bean. It is also able to evaluate path-like
 * sub-queries when variable property value is a JSON or a XML string.<br>
 * For example, if {@code request.body} is a JSON string, you may use {@code request.body/books/1/author} for extracting
 * the author value of first book ;-)
 * @author laurent
 */
public class VariableReferenceExpression implements Expression {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(VariableReferenceExpression.class);

   private static final String ARRAY_INDEX_REGEXP = "\\[(\\d+)\\]";
   private static final String MAP_INDEX_REGEXP = "\\[([\\.\\w-]+)\\]";
   private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile(ARRAY_INDEX_REGEXP);
   private static final Pattern MAP_INDEX_PATTERN = Pattern.compile(MAP_INDEX_REGEXP);

   private static final String[] PROPERTY_NAME_DELIMITERS = { "/", "[" };

   private Object variable;
   private String pathExpression;

   private String variableName;

   /**
    * Create a new expression with a variable and a path (property + sub-query expression).
    * @param variable       Bean from whom to extract value
    * @param pathExpression Path expression to get value from root object (property name + path sub-query)
    */
   public VariableReferenceExpression(Object variable, String pathExpression) {
      this.variable = variable;
      this.pathExpression = pathExpression;
   }

   /**
    * Create a new expression with a variable name (to be searched later into EvaluationContext)
    * @param variableName Name of a variable to get from Evaluation context.
    */
   public VariableReferenceExpression(String variableName) {
      this.variableName = variableName;
   }

   public Object getVariable() {
      return variable;
   }

   public void setVariable(Object variable) {
      this.variable = variable;
   }

   public String getPathExpression() {
      return pathExpression;
   }

   public void setPathExpression(String pathExpression) {
      this.pathExpression = pathExpression;
   }

   @Override
   public String getValue(EvaluationContext context) {
      // Use variable name if we just provide this.
      if (variableName != null && variable == null) {
         variable = context.lookupVariable(variableName);
         return (variable != null ? variable.toString() : "");
      }

      String propertyName = pathExpression;
      String propertyPath = null;
      int delimiterIndex = -1;

      // Search for a delimiter to isolate property name.
      for (String delimiter : PROPERTY_NAME_DELIMITERS) {
         delimiterIndex = pathExpression.indexOf(delimiter);
         if (delimiterIndex != -1) {
            propertyName = pathExpression.substring(0, delimiterIndex);
            propertyPath = pathExpression.substring(delimiterIndex);
            break;
         }
      }
      Object variableValue = getProperty(variable, propertyName);

      if (log.isDebugEnabled()) {
         log.debug("propertyName: {}", propertyName);
         log.debug("propertyPath: {}", propertyPath);
         log.debug("variableValue: {}", variableValue);
      }

      if (propertyPath != null) {
         if (variableValue.getClass().equals(String.class)) {
            variableValue = getStringValue(variableValue, propertyPath);
         } else if (variableValue.getClass().isArray()) {
            variableValue = getArrayValue(variableValue, propertyPath);
         } else if (Map.class.isAssignableFrom(variableValue.getClass())) {
            variableValue = getMapValue(variableValue, propertyPath);
         }
      }

      return String.valueOf(variableValue);
   }

   private static String getStringValue(Object variableValue, String propertyPath) {
      if (propertyPath.startsWith("/")) {
         // This is a JSON Pointer or XPath expression to apply.
         String variableString = String.valueOf(variableValue);

         if (variableString.trim().startsWith("{") || variableString.trim().startsWith("[")) {
            return getJsonPointerValue(variableString, propertyPath);
         } else if (variableString.trim().startsWith("<")) {
            return getXPathValue(variableString, propertyPath);
         } else {
            log.warn("Got a path query expression but content seems not to be JSON nor XML...");
            return null;
         }
      }
      return String.valueOf(variableValue);
   }

   private static Object getArrayValue(Object variableValue, String propertyPath) {
      if (propertyPath.matches(ARRAY_INDEX_REGEXP)) {
         Matcher m = ARRAY_INDEX_PATTERN.matcher(propertyPath);
         if (m.matches()) {
            String arrayIndex = m.group(1);
            Object[] variableValues = (Object[]) variableValue;
            try {
               return variableValues[Integer.parseInt(arrayIndex)];
            } catch (ArrayIndexOutOfBoundsException ae) {
               log.warn("Expression asked for {} but array is smaller ({}). Returning null.", arrayIndex,
                     variableValues.length);
               return null;
            }
         }
      }
      return variableValue;
   }

   private static Object getMapValue(Object variableValue, String propertyPath) {
      if (propertyPath.matches(MAP_INDEX_REGEXP)) {
         Matcher m = MAP_INDEX_PATTERN.matcher(propertyPath);
         if (m.matches()) {
            String mapKey = m.group(1);
            Map<?, ?> variableValues = (Map<?, ?>) variableValue;
            return variableValues.get(mapKey);
         }
      }
      return variableValue;
   }

   /**
    * Fetch a property from an object. For example of you wanted to get the foo property on a bar object you would
    * normally call {@code bar.getFoo()}.
    * @param obj      The object whose property you want to fetch
    * @param property The property name
    * @return The value of the property or null if it does not exist.
    */
   private static Object getProperty(Object obj, String property) {
      Object result = null;

      try {
         String methodName = "get" + property.substring(0, 1).toUpperCase() + property.substring(1);
         Class<?> clazz = obj.getClass();
         Method method = clazz.getMethod(methodName);
         result = method.invoke(obj);
      } catch (Exception e) {
         // Do nothing, we'll return the default value
         log.warn("{} property was requested on {} but cannot find a valid getter", property, obj.getClass(), e);
      }
      return result;
   }

   /** Extract a value from JSON using a JSON Pointer expression. */
   private static String getJsonPointerValue(String jsonText, String jsonPointerExp) {
      // Parse json text ang get root node.
      JsonNode rootNode;
      try {
         ObjectMapper mapper = new ObjectMapper();
         rootNode = mapper.readTree(new StringReader(jsonText));
         // Retrieve evaluated node within JSON tree.
         JsonNode evaluatedNode = rootNode.at(jsonPointerExp);
         // Return serialized array if array type node is referenced by JsonPointer, text value otherwise
         return evaluatedNode.isArray() || evaluatedNode.isObject() ? mapper.writeValueAsString(evaluatedNode)
               : evaluatedNode.asText();
      } catch (Exception e) {
         log.warn("Exception while parsing Json text", e);
         return null;
      }
   }

   /** Extract a value from XML using a XPath expression. */
   private static String getXPathValue(String xmlText, String xPathExp) {
      XPath xpath = XPathFactory.newInstance().newXPath();
      try {
         XPathExpression expression = xpath.compile(xPathExp);
         return expression.evaluate(new InputSource(new StringReader(xmlText)));
      } catch (XPathExpressionException e) {
         log.warn("Exception while compiling/evaluating XPath", e);
         return null;
      }
   }
}
