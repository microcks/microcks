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
package io.github.microcks.domain;

/**
 * Enumeration of the progress phases a TestCaseResult may go through while an asynchronous test is running. This allows
 * clients to observe the readiness of the test machinery (for example, when the message consumer is connected and ready
 * to receive) instead of relying on arbitrary delays. The field is optional: a {@code null} phase means no phase has
 * been reported, in which case clients should fall back to the legacy behavior based on {@code elapsedTime}.
 * @author sebastien
 */
public enum TestCasePhase {
   /** The test machinery is connecting to the tested endpoint (for example, creating the broker consumer). */
   CONNECTING,
   /** The test machinery is connected and waiting for messages to be received on the tested endpoint. */
   WAITING_FOR_MESSAGE,
   /** The test case has completed (messages consumed and validated, or timed-out). */
   DONE
}
