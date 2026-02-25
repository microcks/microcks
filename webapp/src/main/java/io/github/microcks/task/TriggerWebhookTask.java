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
package io.github.microcks.task;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.WebhookRegistration;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.WebhookRegistrationRepository;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Scheduled task that triggers webhook registrations.
 * @author laurent
 */
@Component
public class TriggerWebhookTask {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TriggerWebhookTask.class);

   private final WebhookRegistrationRepository webhookRegistrationRepository;
   private final RequestRepository requestRepository;

   public TriggerWebhookTask(WebhookRegistrationRepository webhookRegistrationRepository,
         RequestRepository requestRepository) {
      this.webhookRegistrationRepository = webhookRegistrationRepository;
      this.requestRepository = requestRepository;
   }

   @Scheduled(fixedRateString = "3s", initialDelayString = "2s")
   public void triggerFastWebhook() {
      log.trace("Triggering webhook registrations for frequency '3s'");
      triggerWebhook(3000L);
   }

   @Scheduled(fixedRateString = "10s", initialDelayString = "2s")
   public void triggerMediumWebhook() {
      log.trace("Triggering webhook registrations for frequency '10s'");
      triggerWebhook(10000L);
   }

   @Scheduled(fixedRateString = "30s", initialDelayString = "2s")
   public void triggerSlowWebhook() {
      log.trace("Triggering webhook registrations for frequency '30s'");
      triggerWebhook(30000L);
   }

   private void triggerWebhook(Long frequency) {
      List<WebhookRegistration> registrations = webhookRegistrationRepository.findByFrequency(frequency);

      // Group registrations by operationId.
      Map<String, List<WebhookRegistration>> registrationsByOperationId = registrations.stream()
            .collect(Collectors.groupingBy(WebhookRegistration::getOperationId));

      List<WebhookRegistration> toRemove = new ArrayList<>();
      List<WebhookRegistration> toUpdate = new ArrayList<>();

      for (Entry<String, List<WebhookRegistration>> operationToRegistrations : registrationsByOperationId.entrySet()) {
         List<Request> requests = requestRepository.findByOperationId(operationToRegistrations.getKey());

         // We need to send each request to each registration.
         long now = System.currentTimeMillis();
         for (WebhookRegistration registration : operationToRegistrations.getValue()) {
            if (registration.getExpiresAt().getTime() > now) {
               // Still valid, try sending webhook request.
               trySendingWebhookRequest(registration, requests, toRemove, toUpdate);
            } else {
               log.debug("Removing expired webhook registration {}", registration);
               toRemove.add(registration);
            }
         }
      }
      // Update the registrations and remove expired/failed ones.
      manageUpdatesAndRemovals(toUpdate, toRemove);
   }

   private void trySendingWebhookRequest(WebhookRegistration registration, List<Request> requests,
         List<WebhookRegistration> toRemove, List<WebhookRegistration> toUpdate) {
      try {
         sendWebhookRequests(registration, requests);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         log.error("Error while sending webhook request to {}", registration.getTargetUrl(), e);
         handleWebhookFailure(registration, toRemove, toUpdate);
      } catch (IOException e) {
         log.error("Error while sending webhook request to {}", registration.getTargetUrl());
         handleWebhookFailure(registration, toRemove, toUpdate);
      }
   }

   private void handleWebhookFailure(WebhookRegistration registration, List<WebhookRegistration> toRemove,
         List<WebhookRegistration> toUpdate) {
      // Increment error count and check against threshold.
      registration.setErrorCount(registration.getErrorCount() + 1);
      if (registration.getErrorCount() >= registration.getErrorCountThreshold()) {
         log.error("Webhook registration {} has reached error threshold {} and will be removed", registration.getId(),
               registration.getErrorCountThreshold());
         toRemove.add(registration);
      } else {
         // Keep track for update.
         toUpdate.add(registration);
      }
   }

   private void manageUpdatesAndRemovals(List<WebhookRegistration> toUpdate, List<WebhookRegistration> toRemove) {
      if (!toUpdate.isEmpty()) {
         log.debug("Updating {} webhook registrations", toUpdate.size());
         webhookRegistrationRepository.saveAll(toUpdate);
      }
      if (!toRemove.isEmpty()) {
         log.debug("Removing {} webhook registrations", toRemove.size());
         webhookRegistrationRepository.deleteAll(toRemove);
      }
   }

   private void sendWebhookRequests(WebhookRegistration registration, List<Request> requests)
         throws IOException, InterruptedException {
      // Build the http client and send the request in a fire and forget mode.
      HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1).build();

      for (Request request : requests) {
         String normalizedMethod = registration.getOperationMethod() == null ? "POST"
               : registration.getOperationMethod().trim().toUpperCase(Locale.ROOT);

         String body = request.getContent();

         boolean methodAllowsBody = !(normalizedMethod.equals("GET") || normalizedMethod.equals("DELETE"));
         HttpRequest.BodyPublisher publisher = (body == null || body.isBlank() || !methodAllowsBody)
               ? HttpRequest.BodyPublishers.noBody()
               : HttpRequest.BodyPublishers.ofString(renderContent(body));

         // Initialize request builder.
         HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(registration.getTargetUrl()))
               .timeout(Duration.ofSeconds(2)).method(normalizedMethod, publisher);

         // Add headers.
         setRequestHeaders(requestBuilder, request, body, methodAllowsBody);

         httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
      }
   }

   private String renderContent(String contentTemplate) {
      if (contentTemplate != null && contentTemplate.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         log.debug("Rendering content template {}", contentTemplate);
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
         return engine.getValue(contentTemplate);
      }
      // No dynamic rendering, just return the template as is.
      return contentTemplate;
   }

   private void setRequestHeaders(HttpRequest.Builder requestBuilder, Request callbackRequest, String body,
         boolean methodAllowsBody) {
      boolean hasContentType = false;
      boolean hasAccept = false;

      // Add headers.
      if (callbackRequest.getHeaders() != null && !callbackRequest.getHeaders().isEmpty()) {
         for (Header header : callbackRequest.getHeaders()) {
            String headerName = header.getName();
            if ("content-type".equalsIgnoreCase(headerName)) {
               hasContentType = true;
            } else if ("accept".equalsIgnoreCase(headerName)) {
               hasAccept = true;
            }
            for (String value : header.getValues()) {
               requestBuilder.header(headerName, value);
            }
         }
      }
      //  Sensible defaults when caller didn't provide them.
      if (!hasAccept) {
         requestBuilder.header("Accept", "*/*");
      }
      if (!hasContentType && body != null && !body.isBlank() && methodAllowsBody) {
         // If your callbacks are not JSON, adjust this default to what your callback expects.
         requestBuilder.header("Content-Type", "application/json; charset=utf-8");
      }
   }
}
