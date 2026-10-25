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
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps the <code>examples</code> entries of an OpenCollection http item to Microcks request/response pairs. Other item
 * types (GraphQL, gRPC, WebSocket) declare examples of another shape and get their own mapper.
 * @author SebastienDegodez
 */
final class OpenCollectionHttpExampleMapper {

   private static final String CONTENT_TYPE_HEADER = "Content-Type";

   /** The media type of each OpenCollection <code>body.type</code>, used when there is no Content-Type header. */
   private static final Map<String, String> MEDIA_TYPES_BY_BODY_TYPE = Map.of("json", "application/json", "xml",
         "application/xml", "text", "text/plain", "html", "text/html", "binary", "application/octet-stream");

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
      response.setHeaders(headersOf(exampleResponse.path("headers")));
      response.setMediaType(contentTypeOf(response.getHeaders())
            .orElseGet(() -> MEDIA_TYPES_BY_BODY_TYPE.get(exampleResponse.path("body").path("type").asText())));
      return response;
   }

   /** The value of the <code>Content-Type</code> header, whatever its case, if any. */
   private static Optional<String> contentTypeOf(Set<Header> headers) {
      return headers.stream().filter(header -> CONTENT_TYPE_HEADER.equalsIgnoreCase(header.getName()))
            .flatMap(header -> header.getValues().stream()).findFirst();
   }

   /** Map the <code>{name, value}</code> list of an example to Microcks headers, one value each. */
   private static Set<Header> headersOf(JsonNode exampleHeaders) {
      Set<Header> headers = new HashSet<>();
      for (JsonNode exampleHeader : exampleHeaders) {
         headers.add(headerOf(exampleHeader));
      }
      return headers;
   }

   private static Header headerOf(JsonNode exampleHeader) {
      Header header = new Header();
      header.setName(exampleHeader.path("name").asText());
      // A mutable set, as other Microcks components may complete header values.
      header.setValues(new HashSet<>(Set.of(exampleHeader.path("value").asText())));
      return header;
   }
}
