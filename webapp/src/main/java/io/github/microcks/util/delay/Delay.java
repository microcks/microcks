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
public class Delay {
   private final Long baseValue;
   private final String strategyName;

   public Delay(Long baseValue, String strategyName) {
      this.baseValue = baseValue;
      this.strategyName = strategyName;
   }

   public Long getBaseValue() {
      return baseValue;
   }

   public String getStrategyName() {
      return strategyName;
   }

   @Override
   public String toString() {
      return "Delay{baseValue=" + baseValue + ", strategyName=" + strategyName + '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (!(o instanceof Delay))
         return false;
      Delay delay = (Delay) o;
      return baseValue == delay.baseValue && strategyName.equals(delay.strategyName);
   }

   @Override
   public int hashCode() {
      return Long.hashCode(baseValue) * 31 + strategyName.hashCode();
   }
}
