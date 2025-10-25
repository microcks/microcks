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

/**
 * DelayApplierFactory is a factory class for creating instances of {@link DelayApplier} based on a given strategy name.
 * It supports three strategies:
 * <ul>
 * <li>"fixed": Returns a fixed delay value.</li>
 * <li>"random": Computes a random delay between 0 and the base value.</li>
 * <li>"random-XX": Computes a random delay within a range defined by a percentage XX (0-100) of the base value.</li>
 * </ul>
 * If the strategy name is null or unrecognized, it defaults to the "fixed" strategy.
 * @author SebastienDegodez
 */
public class DelayApplierFactory {

   /** Private constructor to hide the implicit public one and prevent instantiation. */
   private DelayApplierFactory() {
      // Hidden constructor
   }

   /**
    * Creates a DelayApplier instance based on the provided strategy name.
    * @param strategyName the name of the delay strategy
    * @return an instance of DelayApplier corresponding to the strategy
    */
   public static DelayApplier fromString(String strategyName) {
      if (strategyName == null || strategyName.equalsIgnoreCase(DelayApplierOptions.FIXED)) {
         return new FixedDelayApplier();
      }
      if (strategyName.equalsIgnoreCase(DelayApplierOptions.RANDOM)) {
         return new RandomDelayApplier();
      }
      if (RandomRangedDelayApplier.isRandomRangedStrategy(strategyName)) {
         return new RandomRangedDelayApplier(strategyName);
      }
      // fallback
      return new FixedDelayApplier();
   }
}
