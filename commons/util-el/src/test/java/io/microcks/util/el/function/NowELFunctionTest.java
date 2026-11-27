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

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for NowELFunction class.
 * @author laurent
 */
class NowELFunctionTest {

   @Test
   void testSimpleEvaluation() {
      long before = System.currentTimeMillis();

      // Compute evaluation.
      NowELFunction function = new NowELFunction();
      String result = function.evaluate(null);
      long resultLong = Long.parseLong(result);

      // Get new timestamp and compare.
      long after = System.currentTimeMillis();
      assertTrue(before <= resultLong);
      assertTrue(after >= resultLong);
   }

   @Test
   void testPatternEvaluation() {
      Calendar currentDate = Calendar.getInstance();

      // Compute evaluation.
      NowELFunction function = new NowELFunction();
      String result = function.evaluate(null, "dd/MM/yyyy HH:mm:ss");

      // Assert formatting.
      int day = currentDate.get(Calendar.DAY_OF_MONTH);
      int month = currentDate.get(Calendar.MONTH);
      int year = currentDate.get(Calendar.YEAR);
      String dateString = (day < 10 ? "0" + day : day) + "/" + (++month < 10 ? "0" + month : month) + "/" + year;

      assertTrue(result.startsWith(dateString));
   }

   @Test
   void testPatternDeltaEvaluation() {
      Calendar currentDate = Calendar.getInstance();

      // Compute evaluation.
      NowELFunction function = new NowELFunction();
      String result = function.evaluate(null, "dd/MM/yyyy HH:mm:ss", "1d");

      // Assert formatting.
      currentDate.add(Calendar.DAY_OF_YEAR, 1);
      SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
      String dateString = dateFormat.format(currentDate.getTime());

      assertTrue(result.startsWith(dateString.split(" ")[0]));

      // Now add 1 month.
      result = function.evaluate(null, "dd/MM/yyyy HH:mm:ss", "1M");
      currentDate.add(Calendar.DAY_OF_YEAR, -1);
      currentDate.add(Calendar.MONTH, 1);
      dateString = dateFormat.format(currentDate.getTime());
      assertTrue(result.startsWith(dateString.split(" ")[0]));
   }
}
