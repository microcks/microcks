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

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of API Pastry - 2.0 answering what each test tells it to, conformant to its contract or not. A
 * request it was told nothing about gets a 404 without body.
 * @author SebastienDegodez
 */
final class PastryImplementation implements HttpTransport {

   static final URI BASE_URL = URI.create("http://pastry.example/api");

   private final Map<String, HttpOutcome> answers = new HashMap<>();

   /**
    * @param request     The method and path of a request, such as GET /pastry/Eclair%20Cafe
    * @param status      The status code it answers
    * @param contentType The Content-Type of its answer
    * @param body        The body of its answer
    */
   PastryImplementation answer(String request, int status, String contentType, String body) {
      return answer(request, new HttpOutcome(status, Map.of("content-type", List.of(contentType)), body));
   }

   /**
    * @param request The method and path of a request, such as GET /pastry/Eclair%20Cafe
    * @param answer  What it answers
    */
   PastryImplementation answer(String request, HttpOutcome answer) {
      answers.put(request, answer);
      return this;
   }

   @Override
   public HttpOutcome send(HttpCall call, Duration timeout) {
      String path = call.uri().getRawPath().substring(BASE_URL.getRawPath().length());
      return answers.getOrDefault(call.method() + " " + path, new HttpOutcome(404, Map.of(), ""));
   }
}
