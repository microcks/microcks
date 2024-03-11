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
package io.github.microcks.util.soapui.assertions;

import io.github.microcks.domain.Request;

import org.springframework.http.client.ClientHttpResponse;

/**
 * Simple record for wrapping exchange elements of a test.
 * @param request         The request that was issued to tested endpoint
 * @param response        The response from Http client
 * @param responseContent The body of the response
 * @param duration        The duration of the exchange
 */
public record RequestResponseExchange(Request request, ClientHttpResponse response, String responseContent,
      long duration) {
}
