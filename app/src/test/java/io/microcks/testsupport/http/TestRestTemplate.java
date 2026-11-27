/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.testsupport.http;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A small blocking HTTP client exposing the subset of Spring's TestRestTemplate used by the 1.x integration tests, on
 * top of java.net.http so that the tests keep their shape without any Spring dependency.
 * @author laurent
 */
public class TestRestTemplate {

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private final String baseUrl;
   private final HttpClient client;

   public TestRestTemplate(String baseUrl) {
      this(baseUrl, HttpClient.Redirect.NORMAL);
   }

   private TestRestTemplate(String baseUrl, HttpClient.Redirect redirect) {
      this.baseUrl = baseUrl;
      this.client = HttpClient.newBuilder().followRedirects(redirect).build();
   }

   /** @return A client that does not follow HTTP redirects. */
   public TestRestTemplate withoutRedirects() {
      return new TestRestTemplate(baseUrl, HttpClient.Redirect.NEVER);
   }

   public <T> ResponseEntity<T> getForEntity(String url, Class<T> type) {
      return exchange(url, HttpMethod.GET, null, type);
   }

   public <T> ResponseEntity<T> postForEntity(String url, Object body, Class<T> type) {
      HttpEntity<?> entity = body instanceof HttpEntity<?> httpEntity ? httpEntity : new HttpEntity<>(body);
      return exchange(url, HttpMethod.POST, entity, type);
   }

   public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, Class<T> type) {
      return send(url, method, entity, bytes -> convert(bytes, type));
   }

   public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> entity,
         ParameterizedTypeReference<T> type) {
      return send(url, method, entity, bytes -> convertGeneric(bytes, type.getType()));
   }

   /**
    * Execute a request and give the raw response stream to an extractor (used for long-lived SSE streams).
    * @param url       The relative url
    * @param method    The HTTP method
    * @param request   A callback allowing to customize request headers
    * @param extractor The function reading the response stream
    * @param <T>       The extractor result type
    * @return The extractor result
    */
   public <T> T execute(String url, HttpMethod method, Consumer<HttpHeaders> request,
         Function<StreamResponse, T> extractor) {
      HttpHeaders headers = new HttpHeaders();
      request.accept(headers);
      try {
         HttpResponse<InputStream> response = client.send(builder(url, method, null, headers).build(),
               HttpResponse.BodyHandlers.ofInputStream());
         return extractor.apply(new StreamResponse(response.body()));
      } catch (IOException e) {
         throw new IllegalStateException(e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IllegalStateException(e);
      }
   }

   /** Response whose body is a stream. */
   public static class StreamResponse {
      private final InputStream body;

      StreamResponse(InputStream body) {
         this.body = body;
      }

      public InputStream getBody() {
         return body;
      }
   }

   private <T> ResponseEntity<T> send(String url, HttpMethod method, HttpEntity<?> entity,
         Function<byte[], T> converter) {
      HttpHeaders headers = entity == null ? new HttpHeaders() : entity.getHeaders();
      Object body = entity == null ? null : entity.getBody();
      try {
         HttpResponse<byte[]> response = client.send(builder(url, method, body, headers).build(),
               HttpResponse.BodyHandlers.ofByteArray());
         HttpHeaders responseHeaders = new HttpHeaders();
         response.headers().map().forEach((k, v) -> responseHeaders.put(k, List.copyOf(v)));
         byte[] bytes = response.body();
         T converted = bytes == null || bytes.length == 0 ? null : converter.apply(bytes);
         return new ResponseEntity<>(new HttpStatus(response.statusCode()), responseHeaders, converted);
      } catch (IOException e) {
         throw new IllegalStateException(e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IllegalStateException(e);
      }
   }

   private HttpRequest.Builder builder(String url, HttpMethod method, Object body, HttpHeaders headers) {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + url.replace(" ", "%20")));
      boolean hasContentType = headers.containsKey("Content-Type");
      headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
      HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
      if (body != null) {
         try {
            if (body instanceof String text) {
               publisher = HttpRequest.BodyPublishers.ofString(text);
               if (!hasContentType) {
                  builder.header("Content-Type", "text/plain;charset=UTF-8");
               }
            } else if (body instanceof byte[] bytes) {
               publisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
            } else {
               publisher = HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(body));
               if (!hasContentType) {
                  builder.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
               }
            }
         } catch (IOException e) {
            throw new IllegalStateException(e);
         }
      }
      return builder.method(method.name(), publisher);
   }

   @SuppressWarnings("unchecked")
   private <T> T convert(byte[] bytes, Class<T> type) {
      try {
         if (type == String.class) {
            return (T) new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
         }
         if (type == byte[].class) {
            return (T) bytes;
         }
         return MAPPER.readValue(bytes, type);
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   private <T> T convertGeneric(byte[] bytes, Type type) {
      try {
         return MAPPER.readValue(bytes, MAPPER.constructType(type));
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }
}
