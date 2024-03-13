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

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Implementation of ELFunction that generates a date representation corresponding to current time + an optional amount
 * of time. String format and added time unit are specified using Java Date Format patterns.
 * @author laurent
 */
public class NowELFunction implements ELFunction {

   @Override
   public String evaluate(EvaluationContext evaluationContext, String... args) {
      SimpleDateFormat dateFormat = null;

      if (args != null) {
         switch (args.length) {
            case 1:
               dateFormat = new SimpleDateFormat(args[0]);
               return dateFormat.format(Calendar.getInstance().getTime());
            case 2:
               Calendar now = Calendar.getInstance();
               // 2nd argument is a delta with unit.
               String unit = args[1].substring(args[1].length() - 1);
               String amountStr = args[1].substring(0, args[1].length() - 1);

               if (isInteger(amountStr)) {
                  int amount = Integer.parseInt(amountStr);
                  switch (unit) {
                     case "m":
                        now.add(Calendar.MINUTE, amount);
                        break;
                     case "H":
                        now.add(Calendar.HOUR, amount);
                        break;
                     case "d":
                        now.add(Calendar.DATE, amount);
                        break;
                     case "M":
                        now.add(Calendar.MONTH, amount);
                        break;
                     case "y":
                        now.add(Calendar.YEAR, amount);
                        break;
                     default:
                        break;
                  }
               }
               dateFormat = new SimpleDateFormat(args[0]);
               return dateFormat.format(now.getTime());
            default:
               return String.valueOf(System.currentTimeMillis());
         }
      }
      return String.valueOf(System.currentTimeMillis());
   }

   /** Check if given string is parsable in Integer without throwing exception. */
   private static boolean isInteger(String strNum) {
      if (strNum == null) {
         return false;
      }
      try {
         int d = Integer.parseInt(strNum);
      } catch (NumberFormatException nfe) {
         return false;
      }
      return true;
   }
}
