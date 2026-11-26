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

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Interface definition for Task consuming messages on a remote broker.
 * @author laurent
 */
public interface MessageConsumptionTask extends Callable<List<ConsumedMessage>>, Closeable {

   /**
    * Register a listener that will be notified of the progress phases reached by this consumption task (for example
    * {@link TestCasePhase#WAITING_FOR_MESSAGE} once the broker consumer is connected and ready to receive). This is an
    * optional capability: implementations that cannot detect readiness may keep the default no-op behavior.
    * @param phaseListener The listener to notify of phase changes.
    */
   default void setPhaseListener(Consumer<TestCasePhase> phaseListener) {
      // Optional capability: no-op by default.
   }

}
