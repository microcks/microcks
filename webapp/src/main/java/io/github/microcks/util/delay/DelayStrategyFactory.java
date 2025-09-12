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
 * 
 * @author SebastienDegodez
 */
public class DelayStrategyFactory {
   public static DelayStrategy fromString(String strategyName) {
      if (strategyName == null || strategyName.equalsIgnoreCase("fixed")) {
         return new FixedDelayStrategy();
      }
      if (strategyName.equalsIgnoreCase("random")) {
         return new RandomDelayStrategy();
      }
      if (RandomRangedDelayStrategy.isRandomRangedStrategy(strategyName)) {
         return new RandomRangedDelayStrategy(strategyName);
      }
      // fallback
      return new FixedDelayStrategy();
   }
}
