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
package io.microcks.minion.arazzo.runner;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends the requests of the steps with the HTTP client of the JDK.
 * @author SebastienDegodez
 */
public class JdkHttpTransport implements HttpTransport {

   private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

   @Override
   public HttpOutcome send(HttpCall call, Duration timeout) throws IOException {
      HttpRequest.Builder request = HttpRequest.newBuilder(call.uri()).timeout(timeout).method(call.method(),
            call.body() == null ? HttpRequest.BodyPublishers.noBody()
                  : HttpRequest.BodyPublishers.ofString(call.body()));
      call.headers().forEach(request::header);
      try {
         HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
         return new HttpOutcome(response.statusCode(), response.headers().map(), response.body());
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
         throw new IOException("Interrupted while waiting for the response", ie);
      }
   }
}
