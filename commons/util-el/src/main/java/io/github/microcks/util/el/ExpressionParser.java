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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper object for finding and parsing expressions present into a string template. For example, following template:
 * {@code Hello {{ request.body/name }} it's {{ now() }}} should be decomposed into 4 expressions:
 * <ul>
 * <li>A LiteralExpression representing the "Hello " part</li>
 * <li>A VariableReferenceExpression representing the "request.body/name part</li>
 * <li>A LiteralExpression representing the " it's " part</li>
 * <li>A ELFunctionExpression representing the "now()" part</li>
 * </ul>
 * @author laurent
 */
public class ExpressionParser {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ExpressionParser.class);

   /**
    * Navigate the template for finding expressions that can be evaluated into this template. Expressions are returned
    * into an ordered array.
    * @param template         The string to browse
    * @param context          The EvaluationContext that may contains variable or function references
    * @param expressionPrefix The prefix starting new expression (ex: "{{")
    * @param expressionSuffix The suffix closing expression (ex: "}}")
    * @return The array of found expressions when browsing template from left to right
    */
   public static Expression[] parseExpressions(String template, EvaluationContext context, String expressionPrefix,
         String expressionSuffix) throws ParseException {
      // Prepare an array for results.
      List<Expression> expressions = new ArrayList<>();
      int startIdx = 0;

      while (startIdx < template.length()) {
         int prefixIndex = template.indexOf(expressionPrefix, startIdx);
         if (prefixIndex >= startIdx) {
            // an inner expression was found - this is a composite
            if (prefixIndex > startIdx) {
               log.debug("Found a literal expression starting at {}", startIdx);
               expressions.add(new LiteralExpression(template.substring(startIdx, prefixIndex)));
            }
            int afterPrefixIndex = prefixIndex + expressionPrefix.length();
            int suffixIndex = skipToCorrectEndSuffix(expressionSuffix, template, afterPrefixIndex);
            if (suffixIndex == -1) {
               log.info("No ending suffix '{}' for expression starting at character {}: {}", expressionSuffix,
                     prefixIndex, template.substring(prefixIndex));
               throw new ParseException(template, prefixIndex,
                     "No ending suffix '" + expressionSuffix + "' for expression starting at character " + prefixIndex
                           + ": " + template.substring(prefixIndex));
            }
            if (suffixIndex == afterPrefixIndex) {
               log.info("No expression defined within delimiter '{}' at character {}",
                     expressionPrefix + expressionSuffix, prefixIndex);
               throw new ParseException(template, prefixIndex, "No expression defined within delimiter '"
                     + expressionPrefix + expressionSuffix + "' at character " + prefixIndex);
            }
            String expr = template.substring(prefixIndex + expressionPrefix.length(), suffixIndex);
            expr = expr.trim();
            if (expr.isEmpty()) {
               log.info("No expression defined within delimiter '{}' at character {}",
                     expressionPrefix + expressionSuffix, prefixIndex);
               throw new ParseException(template, prefixIndex, "No expression defined within delimiter '"
                     + expressionPrefix + expressionSuffix + "' at character " + prefixIndex);
            }
            expressions.add(doParseExpression(expr, context));
            startIdx = suffixIndex + expressionSuffix.length();
            log.debug("Expression accumulated. Pursuing with index {} on {}", startIdx, template.length());
         } else {
            // no more expression. finalize with a literal.
            expressions.add(new LiteralExpression(template.substring(startIdx, template.length())));
            break;
         }
      }
      return expressions.toArray(new Expression[0]);
   }

   /** Find for next suitable correct end suffix. Could be extended in future to manager recursivity... */
   private static int skipToCorrectEndSuffix(String expressionSuffix, String template, int afterPrefixIndex) {
      int nextSuffix = template.indexOf(expressionSuffix, afterPrefixIndex);
      if (nextSuffix == -1) {
         return -1; // the suffix is missing
      }
      return nextSuffix;
   }

   /**
    * Depending on expression string, try to guess if it's a Redirect, a Literal, a Function or a VariableReference
    * expression.
    */
   private static Expression doParseExpression(String expressionString, EvaluationContext context) {

      boolean hasRedirect = expressionString.indexOf('>') != -1;
      log.debug("hasRedirect:{}", hasRedirect);

      if (hasRedirect) {
         String[] parts = expressionString.split("\\>");
         Expression[] expressions = new Expression[parts.length];
         for (int i = 0; i < parts.length; i++) {
            expressions[i] = doParseSimpleExpression(parts[i].trim(), context);
         }
         return new RedirectExpression(expressions);
      }
      // Else pare simple expression.
      return doParseSimpleExpression(expressionString, context);
   }

   /**
    * Depending on expression string, try to guess if it's a Literal, a Function or a VariableReference expression.
    */
   private static Expression doParseSimpleExpression(String expressionString, EvaluationContext context) {
      int argsStart = expressionString.indexOf('(');
      int argsEnd = expressionString.indexOf(')');
      int variableStart = expressionString.indexOf('.');

      boolean hasVariable = variableStart != -1;
      boolean hasArgs = (argsStart != 1 && argsEnd != -1 && argsStart < argsEnd);
      boolean isPostmanFunction = expressionString.startsWith("$");
      boolean varBeforeArgs = (variableStart < argsStart) && !isPostmanFunction;

      log.debug("hasVariable:{} hasArgs:{} isPostmanFunction:{} varBeforeArgs:{}", hasVariable, hasArgs,
            isPostmanFunction, varBeforeArgs);

      // Check if it's a VariableReferenceExpression.
      if (hasVariable && (!hasArgs || varBeforeArgs)) {
         log.debug("Found a variable reference expression {}", expressionString);
         String variableName = expressionString.substring(0, expressionString.indexOf('.'));
         Object variable = context.lookupVariable(variableName);
         String pathExpression = expressionString.substring(expressionString.indexOf('.') + 1);

         if (variable != null) {
            return new VariableReferenceExpression(variable, pathExpression);
         }
         log.warn("Variable with name {} cannot be found into EvaluationContext. Returning empty literal expression",
               variableName);
         return new LiteralExpression("");
      }

      // Check if it's a ELFunctionExpression
      if (hasArgs || isPostmanFunction) {
         log.debug("Found a function expression {}", expressionString);

         String functionName = null;
         String[] args = new String[0];
         // Checking for easier Postman compatibility notation first.
         if (expressionString.startsWith("$")) {
            functionName = expressionString.substring(1);
         } else {
            functionName = expressionString.substring(0, argsStart);
            String argsString = expressionString.substring(argsStart + 1, argsEnd);
            // Parse arguments if non empty string.
            if (argsString.length() > 0) {
               args = Arrays.stream(argsString.split(",")).map(arg -> arg.trim()).toArray(String[]::new);
            }
         }

         Class<ELFunction> functionClazz = context.lookupFunction(functionName);
         ELFunction function = null;
         try {
            function = functionClazz.newInstance();
         } catch (Exception e) {
            log.error("Exception while instantiating the functionClazz " + functionClazz, e);
            return new LiteralExpression("");
         }
         return new FunctionExpression(function, args);
      }

      log.info("No ELFunction or complex VariableReference expressions found... Returning simple VariableReference");
      return new VariableReferenceExpression(expressionString);
   }
}
