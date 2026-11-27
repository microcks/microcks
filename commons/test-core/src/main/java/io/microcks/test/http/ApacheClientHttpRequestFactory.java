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
package io.microcks.test.http;

import io.microcks.util.http.ClientHttpRequest;
import io.microcks.util.http.ClientHttpRequestFactory;
import io.microcks.util.http.ClientHttpResponse;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.http.HttpMethod;
import io.microcks.util.http.HttpStatusCode;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

/**
 * A {@code ClientHttpRequestFactory} backed by an Apache HttpClient 5 instance, so that connection management, proxy
 * and TLS trust configuration stay under the caller's control.
 * @author laurent
 */
public class ApacheClientHttpRequestFactory implements ClientHttpRequestFactory {

   private final CloseableHttpClient httpClient;

   /**
    * Build a factory on a pre-configured client.
    * @param httpClient The Apache HTTP client to use
    */
   public ApacheClientHttpRequestFactory(CloseableHttpClient httpClient) {
      this.httpClient = httpClient;
   }

   @Override
   public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
      return new ApacheRequest(new HttpUriRequestBase(httpMethod.methodName(), uri));
   }

   private class ApacheRequest implements ClientHttpRequest {
      private final HttpUriRequestBase request;
      private final HttpHeaders headers = new HttpHeaders();
      private final ByteArrayOutputStream body = new ByteArrayOutputStream();

      ApacheRequest(HttpUriRequestBase request) {
         this.request = request;
      }

      @Override
      public HttpHeaders getHeaders() {
         return headers;
      }

      @Override
      public OutputStream getBody() {
         return body;
      }

      @Override
      public ClientHttpResponse execute() throws IOException {
         for (Map.Entry<String, java.util.List<String>> header : headers.headerSet()) {
            for (String value : header.getValue()) {
               request.addHeader(header.getKey(), value);
            }
         }
         if (body.size() > 0) {
            String contentType = headers.getContentType();
            request.setEntity(
                  new ByteArrayEntity(body.toByteArray(), contentType != null ? ContentType.parse(contentType) : null));
         }
         return new ApacheResponse(httpClient.execute(request));
      }
   }

   private static class ApacheResponse implements ClientHttpResponse {
      private final CloseableHttpResponse response;
      private final HttpHeaders headers = new HttpHeaders();

      ApacheResponse(CloseableHttpResponse response) {
         this.response = response;
         for (Header header : response.getHeaders()) {
            headers.add(header.getName(), header.getValue());
         }
      }

      @Override
      public HttpStatusCode getStatusCode() {
         return new HttpStatusCode(response.getCode());
      }

      @Override
      public HttpHeaders getHeaders() {
         return headers;
      }

      @Override
      public InputStream getBody() throws IOException {
         return response.getEntity() != null ? response.getEntity().getContent() : InputStream.nullInputStream();
      }

      @Override
      public void close() {
         try {
            response.close();
         } catch (IOException e) {
            // Nothing to recover from while closing.
         }
      }
   }
}
