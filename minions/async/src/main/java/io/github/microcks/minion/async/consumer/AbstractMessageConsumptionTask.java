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
package io.github.microcks.minion.async.consumer;

import io.github.microcks.domain.TestCasePhase;

import java.util.function.Consumer;

/**
 * Base class for {@link MessageConsumptionTask} implementations that factorizes the optional progress phase reporting.
 * Subclasses just have to call {@link #notifyWaitingForMessage()} once their broker consumer is connected and ready to
 * receive messages, so that clients can observe readiness instead of relying on arbitrary delays.
 * @author sebastien
 */
public abstract class AbstractMessageConsumptionTask implements MessageConsumptionTask {

   private Consumer<TestCasePhase> phaseListener;

   @Override
   public void setPhaseListener(Consumer<TestCasePhase> phaseListener) {
      this.phaseListener = phaseListener;
   }

   /**
    * Notify the registered phase listener (if any) that the consumer is now connected and waiting for messages. Safe to
    * call when no listener has been registered.
    */
   protected void notifyWaitingForMessage() {
      if (phaseListener != null) {
         phaseListener.accept(TestCasePhase.WAITING_FOR_MESSAGE);
      }
   }
}
