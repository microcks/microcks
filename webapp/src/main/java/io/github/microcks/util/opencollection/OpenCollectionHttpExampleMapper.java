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
package io.github.microcks.util.opencollection;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Maps the <code>examples</code> entries of an OpenCollection http item to Microcks request/response pairs. Other item
 * types (GraphQL, gRPC, WebSocket) declare examples of another shape and get their own mapper.
 * @author SebastienDegodez
 */
final class OpenCollectionHttpExampleMapper {

   private OpenCollectionHttpExampleMapper() {
      // Private constructor to hide the implicit one as it's a utility class.
   }

   /** Build the request/response pair of one example: both sides are named after the example. */
   static Exchange exchangeOf(JsonNode example) {
      String name = example.path("name").asText();
      Request request = new Request();
      request.setName(name);
      return new RequestResponsePair(request, responseOf(name, example.path("response")));
   }

   /** Build the response of an example: its body data is the content, kept exactly as written. */
   private static Response responseOf(String name, JsonNode exampleResponse) {
      Response response = new Response();
      response.setName(name);
      response.setStatus(exampleResponse.path("status").asText());
      response.setContent(exampleResponse.path("body").path("data").textValue());
      return response;
   }
}
