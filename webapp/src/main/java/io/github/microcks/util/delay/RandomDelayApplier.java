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
 * A {@link DelayApplier} that computes a random delay between 0 and the base value.
 * <p>
 * Instances should be obtained via the factory method in {@link DelayApplierFactory} to ensure proper usage and
 * encapsulation.
 * </p>
 * @author SebastienDegodez
 */
public class RandomDelayApplier implements DelayApplier {

   RandomDelayApplier() {
   }

   @Override
   public long compute(long baseValue) {
      if (baseValue > 0) {
         double factor = Math.random();
         return Math.round(baseValue * factor);
      }
      return 0L;
   }

   @Override
   public String getName() {
      return DelayApplierOptions.RANDOM;
   }
}
