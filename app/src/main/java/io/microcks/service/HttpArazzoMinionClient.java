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
package io.microcks.service;

import io.microcks.util.ObjectMapperFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Hands the runs of Arazzo workflows to the Arazzo minion over HTTP, on its POST /api/runs endpoint.
 * @author SebastienDegodez
 */
public class HttpArazzoMinionClient implements ArazzoMinionClient {

   private final String arazzoMinionUrl;
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

   /**
    * @param arazzoMinionUrl The URL of the Arazzo minion
    */
   public HttpArazzoMinionClient(String arazzoMinionUrl) {
      this.arazzoMinionUrl = arazzoMinionUrl;
   }

   @Override
   public void launch(ArazzoRunRequest request) throws ArazzoMinionException {
      try {
         String body = ObjectMapperFactory.getJsonObjectMapper().writeValueAsString(request);
         HttpRequest post = HttpRequest.newBuilder(URI.create(arazzoMinionUrl + "/api/runs"))
               .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
               .POST(HttpRequest.BodyPublishers.ofString(body)).build();
         HttpResponse<Void> response = httpClient.send(post, HttpResponse.BodyHandlers.discarding());
         if (response.statusCode() >= 300) {
            throw new ArazzoMinionException("The Arazzo minion at " + arazzoMinionUrl + " did not receive the run",
                  new IOException("Answered with status " + response.statusCode()));
         }
      } catch (JsonProcessingException jpe) {
         throw new ArazzoMinionException("The run cannot be written as JSON", jpe);
      } catch (IOException ioe) {
         throw new ArazzoMinionException("The Arazzo minion at " + arazzoMinionUrl + " did not receive the run", ioe);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
         throw new ArazzoMinionException("Interrupted while handing the run to the Arazzo minion", ie);
      }
   }
}
