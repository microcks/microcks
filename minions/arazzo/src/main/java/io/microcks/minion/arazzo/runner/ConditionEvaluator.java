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
package io.microcks.minion.arazzo.runner;

import io.microcks.artifact.arazzo.ArazzoCriterion;
import io.microcks.artifact.arazzo.ArazzoExpressionEvaluator;
import io.microcks.artifact.arazzo.ArazzoExpressionException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates the success criteria of a step. A simple condition compares literals (numbers, quoted strings, booleans,
 * null) and runtime expressions with {@code == != < <= > >=}, combined with {@code &&}, which binds tighter, and
 * {@code ||}. A regex criterion matches its pattern against the value of its context.
 * @author SebastienDegodez
 */
final class ConditionEvaluator {

   private static final String REGEX = "regex";
   private static final Pattern TOKEN = Pattern.compile("\\s*('[^']*'|&&|\\|\\||==|!=|<=|>=|<|>|[^\\s=!<>&|']+)");
   private static final Set<String> COMPARATORS = Set.of("==", "!=", "<", "<=", ">", ">=");

   private final ArazzoExpressionEvaluator expressions = new ArazzoExpressionEvaluator();

   /**
    * @param criterion A success criterion of a step
    * @param context   What its runtime expressions can see
    * @return Whether it holds, with the values of its runtime expressions
    * @throws ArazzoExpressionException if the condition is malformed or one of its expressions has no value
    */
   CriterionResult evaluate(ArazzoCriterion criterion, EvaluationContext context) {
      List<ExpressionValue> values = new ArrayList<>();
      if (criterion.type().equals(REGEX)) {
         Object subject = expressions.evaluate(criterion.context(), context);
         values.add(new ExpressionValue(criterion.context(), subject));
         return new CriterionResult(criterion.condition(), values,
               Pattern.compile(criterion.condition()).matcher(String.valueOf(subject)).find());
      }
      Cursor cursor = new Cursor(tokenize(criterion.condition()), criterion.condition(), context, values);
      boolean held = or(cursor);
      if (cursor.hasNext()) {
         throw cursor.invalid();
      }
      return new CriterionResult(criterion.condition(), values, held);
   }

   /** Every operand is evaluated, so that the values of all the expressions of the condition are known. */
   private boolean or(Cursor cursor) {
      boolean result = and(cursor);
      while (cursor.accept("||")) {
         result = and(cursor) | result;
      }
      return result;
   }

   private boolean and(Cursor cursor) {
      boolean result = comparison(cursor);
      while (cursor.accept("&&")) {
         result = comparison(cursor) & result;
      }
      return result;
   }

   private boolean comparison(Cursor cursor) {
      Object left = operand(cursor);
      if (!cursor.nextIsComparator()) {
         if (left instanceof Boolean value) {
            return value;
         }
         throw new ArazzoRunException("Not a boolean: " + left + " in condition: " + cursor.condition);
      }
      String operator = cursor.next();
      return compare(left, operator, operand(cursor));
   }

   private Object operand(Cursor cursor) {
      String token = cursor.next();
      if (token.startsWith("'")) {
         return token.substring(1, token.length() - 1);
      }
      if (token.startsWith("$")) {
         Object value = expressions.evaluate(token, cursor.context);
         cursor.values.add(new ExpressionValue(token, value));
         return value;
      }
      if (token.equals("true") || token.equals("false")) {
         return Boolean.valueOf(token);
      }
      if (token.equals("null")) {
         return null;
      }
      try {
         return new BigDecimal(token);
      } catch (NumberFormatException nfe) {
         throw cursor.invalid();
      }
   }

   private static boolean compare(Object left, String operator, Object right) {
      if (left instanceof Number l && right instanceof Number r) {
         int comparison = new BigDecimal(l.toString()).compareTo(new BigDecimal(r.toString()));
         return switch (operator) {
            case "==" -> comparison == 0;
            case "!=" -> comparison != 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            default -> comparison >= 0;
         };
      }
      return switch (operator) {
         case "==" -> Objects.equals(left, right);
         case "!=" -> !Objects.equals(left, right);
         default ->
            throw new ArazzoRunException("Operator " + operator + " needs numbers, got: " + left + " and " + right);
      };
   }

   private static List<String> tokenize(String condition) {
      String text = condition.strip();
      List<String> tokens = new ArrayList<>();
      Matcher matcher = TOKEN.matcher(text);
      int position = 0;
      while (position < text.length()) {
         matcher.region(position, text.length());
         if (!matcher.lookingAt()) {
            throw new ArazzoRunException("Invalid condition: " + condition);
         }
         tokens.add(matcher.group(1));
         position = matcher.end();
      }
      return tokens;
   }

   /** Reads the tokens of a condition, keeping the values of its runtime expressions. */
   private static final class Cursor {

      private final List<String> tokens;
      private final String condition;
      private final EvaluationContext context;
      private final List<ExpressionValue> values;
      private int index;

      Cursor(List<String> tokens, String condition, EvaluationContext context, List<ExpressionValue> values) {
         this.tokens = tokens;
         this.condition = condition;
         this.context = context;
         this.values = values;
      }

      boolean hasNext() {
         return index < tokens.size();
      }

      String next() {
         if (!hasNext()) {
            throw invalid();
         }
         return tokens.get(index++);
      }

      boolean accept(String token) {
         if (hasNext() && tokens.get(index).equals(token)) {
            index++;
            return true;
         }
         return false;
      }

      boolean nextIsComparator() {
         return hasNext() && COMPARATORS.contains(tokens.get(index));
      }

      ArazzoRunException invalid() {
         return new ArazzoRunException("Invalid condition: " + condition);
      }
   }
}
