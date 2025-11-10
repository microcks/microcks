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
package io.github.microcks.util.tracing;

public enum CommonEvents {

   /** Event emitted when an invocation is initially received by the system. */
   INVOCATION_RECEIVED("invocation_received"),

   /** Event emitted when a body content has been correctly parsed, decrypted, converted, ... */
   BODY_PARSED("body_parsed"),

   /** Event emitted when a dispatcher and its rules are selected for processing the invocation. */
   DISPATCHER_SELECTED("dispatcher_selected"),

   /** Event emitted when a parameter constraint validation fails. */
   PARAMETER_CONSTRAINT_VIOLATED("parameter_constraint_violated"),

   /** Event emitted when dispatch criteria have been computed from the request and dispatcher rules. */
   DISPATCH_CRITERIA_COMPUTED("dispatch_criteria_computed"),

   /** Event emitted when the response lookup process has been completed with computed dispatch criteria. */
   RESPONSE_LOOKUP_COMPLETED("response_lookup_completed"),

   /** Event emitted when a fallback response is used because no matching response was found. */
   FALLBACK_RESPONSE_USED("fallback_response_used"),

   /** Event emitted when a response delay has been configured for the invocation. */
   DELAY_CONFIGURED("delay_configured"),

   /** Event emitted when a request is being proxied to an external service. */
   PROXY_REQUEST_INITIATED("proxy_request_initiated"),

   /** Event emitted when no dispatcher is configured and the system attempts to find any response for the operation. */
   NO_DISPATCHER_FALLBACK_ATTEMPTED("no_dispatcher_fallback_attempted"),

   /** Event emitted when no matching response is found despite having a dispatcher (returns 400 error). */
   NO_RESPONSE_FOUND("no_response_found"),

   /** Event emitted when a specific response has been selected to return to the client. */
   RESPONSE_SELECTED("response_selected"),

   /** Event emitted when no response could be found or generated (returns 400 error). */
   NO_RESPONSE_AVAILABLE("no_response_available");

   private final String eventName;

   CommonEvents(String eventName) {
      this.eventName = eventName;
   }

   public String getEventName() {
      return eventName;
   }
}
