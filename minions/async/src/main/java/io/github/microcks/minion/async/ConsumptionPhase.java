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
package io.github.microcks.minion.async;

/**
 * Enumeration representing the different phases of message consumption for better observability.
 * @author laurent
 */
public enum ConsumptionPhase {
   /** Initial phase when starting the consumption task */
   STARTING,
   /** Phase when establishing connection to the message broker */
   CONNECTING,
   /** Phase when waiting for messages after successful connection */
   WAITING_FOR_MESSAGES,
   /** Phase when messages have been received successfully */
   COMPLETED,
   /** Phase when an error or timeout occurred */
   FAILED
}
