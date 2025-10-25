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
package io.github.microcks.event;

/**
 * A trace event that can be sent to subscribers.
 * @param traceId       the trace identifier
 * @param service       the service name
 * @param operation     the operation name
 * @param clientAddress the client address
 */
public record TraceEvent(String traceId, String service, String operation, String clientAddress) {
}
