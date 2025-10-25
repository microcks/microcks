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
package io.github.microcks.util.delay;

import java.util.regex.Matcher;

/**
 * A {@link DelayApplier} implementation that computes a random delay within a range defined by a percentage. The
 * resulting delay will be randomly distributed between (100 - percent)% and (100 + percent)% of the base value. For
 * example, with percent=20 and baseValue=100, the delay will be between 80 and 120.
 * <p>
 * Instances should be obtained via the factory method in {@link DelayApplierFactory} to ensure proper usage and
 * encapsulation. Direct instantiation is discouraged.
 * </p>
 * @author SebastienDegodez
 */
public class RandomRangedDelayApplier implements DelayApplier {

   /** Check if the strategy name matches the random ranged pattern. */
   public static boolean isRandomRangedStrategy(String strategyName) {
      return DelayApplierOptions.RANDOM_RANGED_PATTERN.matcher(strategyName).matches();
   }

   private final int percent;

   RandomRangedDelayApplier(String strategyName) {
      this.percent = extractPercent(strategyName);
   }

   private int extractPercent(String strategyName) {
      Matcher matcher = DelayApplierOptions.RANDOM_RANGED_PATTERN.matcher(strategyName.toLowerCase());
      if (matcher.matches()) {
         return Integer.parseInt(matcher.group(1));
      }
      // exception 
      throw new IllegalArgumentException("Invalid random range strategy format: " + strategyName
            + ". Expected format is 'random-XX' with XX between 0 and 100.");
   }

   @Override
   public long compute(long baseValue) {
      if (baseValue > 0) {
         double factor = (Math.random() * (2 * this.percent) + (100 - this.percent)) / 100;
         return Math.round(baseValue * factor);
      }
      return baseValue;
   }

   @Override
   public String getName() {
      return String.format(DelayApplierOptions.RANDOM_RANGED_FORMAT, this.percent);

   }
}
