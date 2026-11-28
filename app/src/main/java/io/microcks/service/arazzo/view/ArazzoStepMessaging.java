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
package io.microcks.domain.arazzo;

/**
 * What a step of an AsyncAPI source of an Arazzo workflow tells of the messages it sends or receives.
 * @param action        send or receive; null when the step does not tell it, its AsyncAPI operation then telling it
 * @param correlationId The runtime expression or value identifying the message a receive step waits for; null if none
 * @param timeout       How long the step waits, in milliseconds; null when the step does not tell it
 * @author SebastienDegodez
 */
public record ArazzoStepMessaging(String action, String correlationId, Long timeout) {
}
