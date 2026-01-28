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
package io.github.microcks.listener;

import io.github.microcks.domain.CallbackInfo;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Request;
import io.github.microcks.event.CallbackTriggerEvent;
import io.github.microcks.event.HttpServletRequestSnapshot;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.util.IdBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Application event listener that calls back a consumer endpoint on trigger event.
 * @author laurent
 */
@Component
public class CallbackTrigger implements ApplicationListener<CallbackTriggerEvent> {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(CallbackTrigger.class);

   private final RequestRepository requestRepository;
   private final ApplicationContext applicationContext;


   public CallbackTrigger(RequestRepository requestRepository, ApplicationContext applicationContext) {
      this.requestRepository = requestRepository;
      this.applicationContext = applicationContext;
   }

   @Override
   @Async
   public void onApplicationEvent(CallbackTriggerEvent event) {
      log.debug("Received a CallbackTriggerEvent on {}", event.getServiceId());

      // Find the callbackInfo and callback name.
      CallbackInfo callbackInfo = selectCallbackInfo(event);
      String callbackName = event.getOperation().getCallbackInfos().entrySet().stream()
            .filter(entry -> entry.getValue().equals(callbackInfo)).findFirst().get().getKey();

      // Check if we're able to find a callback url.
      String callbackUrlLocation = callbackInfo.getCallbackUrlExpression();
      String callbackUrl = extractCallbackUrlFromRequest(event.getRequest(), callbackUrlLocation);

      if (callbackUrl != null) {
         log.debug("Extracted callback URL {}", callbackUrl);
         List<Request> requests = requestRepository.findByOperationIdAndName(
               IdBuilder.buildOperationId(event.getServiceId(), event.getOperation()), event.getResponseName());

         // Now find the appropriate callback request.
         Request callbackRequest = requests.stream()
               .filter(request -> request.getCallbackName() != null && request.getCallbackName().equals(callbackName))
               .findFirst().orElse(null);

         if (callbackRequest != null) {
            try {
               Thread.sleep(Duration.ofSeconds(3));
            } catch (InterruptedException e) {
               log.warn("Interrupted while simulating waiting for callback request to be send", e);
            }

            log.debug("Calling callback URL {} with request {}", callbackUrl, callbackRequest.getName());
            // Render templates in content if any and send.
            callbackRequest.setContent(ListenerCommons.renderContent(event.getRequest(), callbackRequest.getContent()));
            sendCallbackRequest(callbackUrl, callbackInfo.getMethod(), callbackRequest);

            // Should we re-emit another CallbackTriggerEvent for next in order?
            if (event.getOperation().getCallbackInfos().size() > event.getStep() + 1) {
               log.debug("Re-emitting CallbackTriggerEvent for next callbackInfo");
               applicationContext.publishEvent(new CallbackTriggerEvent(this, event.getServiceId(),
                     event.getOperation(), event.getResponseName(), event.getRequest(), event.getStep() + 1));
            }
         } else {
            log.warn("No callback request found for callbackName {} on operation {}", callbackName,
                  event.getOperation().getName());
         }
      } else {
         log.warn("No callback URL found for callbackInfo {} on operation {}", callbackInfo,
               event.getOperation().getName());
      }
   }

   /** Select the correct callback info from event operation. */
   private CallbackInfo selectCallbackInfo(CallbackTriggerEvent event) {
      // Sort by order the CallbackInfo from included event operation.
      List<CallbackInfo> infos = event.getOperation().getCallbackInfos().values().stream()
            .sorted((info1, info2) -> Comparator.comparingInt(CallbackInfo::getOrder).compare(info1, info2)).toList();

      log.debug("Ordered callbackInfos:");
      for (CallbackInfo info : infos) {
         log.debug(" - CallbackInfo order: {}, expression: {}", info.getOrder(), info.getCallbackUrlExpression());
      }

      if (infos.size() > event.getStep()) {
         return infos.get(event.getStep());
      }
      return infos.getFirst();
   }

   /** Extract the URL from snapshot request using the callbackUrlLocation expression. */
   private String extractCallbackUrlFromRequest(HttpServletRequestSnapshot request, String callbackUrlLocation) {
      // Sanitize location that starts with {$ and ends with }.
      callbackUrlLocation = callbackUrlLocation.substring(2, callbackUrlLocation.length() - 1);
      // Check if we have a reference to a request header, a query parameter or the body content.
      String[] parts = callbackUrlLocation.split("\\.");
      if (parts.length > 1) {
         String location = parts[1];
         switch (location) {
            case "query":
               String paramName = parts[2];
               log.debug("Extracting query parameter {} from request", paramName);
               return request.queryParameters().get(paramName)[0];
            case "header":
               String headerName = parts[2];
               log.debug("Extracting header value {} from request", headerName);
               return request.headers().get(headerName).getFirst();
            default:
               if (location.startsWith("body#")) {
                  String jsonPointerExp = location.substring("body#".length());
                  log.debug("Extracting JSON pointer expresseion {} from request", jsonPointerExp);
                  try {
                     ObjectMapper mapper = new ObjectMapper();
                     JsonNode rootNode = mapper.readTree(new StringReader(request.body()));
                     JsonNode evaluatedNode = rootNode.at(jsonPointerExp);
                     // Return serialized array if array type node is referenced by JsonPointer, text value otherwise
                     return evaluatedNode.isArray() || evaluatedNode.isObject()
                           ? mapper.writeValueAsString(evaluatedNode)
                           : evaluatedNode.asText();
                  } catch (Exception e) {
                     log.warn("Exception while parsing Json text", e);
                  }
               }
         }
      }
      return null;
   }

   /** Send the callback request to URL using HttpClient. */
   private void sendCallbackRequest(String callbackUrl, String method, Request callbackRequest) {
      if (callbackRequest.getQueryParameters() != null && !callbackRequest.getQueryParameters().isEmpty()) {
         String query = callbackRequest.getQueryParameters().stream()
               .map(p -> urlEncode(p.getName()) + "=" + urlEncode(p.getValue())).collect(Collectors.joining("&"));

         callbackUrl += (callbackUrl.contains("?") ? "&" : "?") + query;
      }

      String normalizedMethod = method == null ? "POST" : method.trim().toUpperCase(Locale.ROOT);
      String body = callbackRequest.getContent();

      boolean methodAllowsBody = !(normalizedMethod.equals("GET") || normalizedMethod.equals("DELETE"));
      HttpRequest.BodyPublisher publisher = (body == null || body.isBlank() || !methodAllowsBody)
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);

      // Initialize request builder.
      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(callbackUrl))
            .timeout(Duration.ofSeconds(10)).method(normalizedMethod, publisher);

      setRequestHeaders(requestBuilder, callbackRequest, body, methodAllowsBody);

      // Build the http client and send the request in a fire and forget mode.
      HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1).build();

      httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
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

   private static String urlEncode(String raw) {
      if (raw == null) {
         return "";
      }
      return URLEncoder.encode(raw, StandardCharsets.UTF_8);
   }
}
