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
package io.github.microcks.minion.async.consumer;

import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncTestSpecification;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.Headers;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a topic on an NATS. Endpoint URL should be
 * specified using the following form: <code>nats://{brokerhost[:port]}</code>
 * @author laurent
 */
public class NATSMessageConsumptionTask implements MessageConsumptionTask {

   /**
    * Get a JBoss logging logger.
    */
   private final Logger logger = Logger.getLogger(getClass());

   /**
    * The string for Regular Expression that helps validating acceptable endpoints.
    */
   public static final String ENDPOINT_PATTERN_STRING = "nats://(?<brokerUrl>[^:]+(:\\d+)?)/(?<topic>.+)";

   /**
    * The Pattern for matching groups within the endpoint regular expression.
    */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   private AsyncTestSpecification specification;

   private Connection subscriber;

   private String endpointTopic;

   private String options;

   /**
    * Create a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public NATSMessageConsumptionTask(AsyncTestSpecification testSpecification) {
      this.specification = testSpecification;
   }

   /**
    * Convenient static method for checking if this implementation will accept endpoint.
    * @param endpointUrl The endpoint URL to validate
    * @return True if endpointUrl can be used for connecting and consuming on endpoint
    */
   public static boolean acceptEndpoint(String endpointUrl) {
      return endpointUrl != null && endpointUrl.matches(ENDPOINT_PATTERN_STRING);
   }

   @Override
   public List<ConsumedMessage> call() throws Exception {
      if (subscriber == null) {
         intializeNATSClient();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      // Start subscribing to the broker endpoint topic.
      Dispatcher d = subscriber.createDispatcher((msg) -> {
         String msgString = new String(msg.getData(), StandardCharsets.UTF_8);
         logger.info("Received a new NATS Message: " + msgString);
         // Build a ConsumedMessage from NATS message.
         ConsumedMessage message = new ConsumedMessage();
         message.setReceivedAt(System.currentTimeMillis());
         message.setHeaders(buildHeaders(msg.getHeaders()));
         message.setPayload(msg.getData());
         messages.add(message);
      });

      d.subscribe(endpointTopic);

      Thread.sleep(specification.getTimeoutMS());

      // Disconnect the subscriber before returning results.
      subscriber.close();
      return messages;
   }

   /** Build set of Microcks headers from NATS headers. */
   private Set<Header> buildHeaders(Headers headers) {
      if (headers == null || headers.isEmpty()) {
         return null;
      }
      Set<Header> results = new HashSet<>();
      for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
         Header result = new Header();
         result.setName(entry.getKey());
         result.setValues(new HashSet<>(entry.getValue()));
         results.add(result);
      }
      return results;
   }

   /**
    * Close the resources used by this task. Namely the NATS subscriber and the option truststore.
    * @throws IOException should not happen.
    */
   @Override
   public void close() throws IOException {
      if (subscriber != null) {
         try {
            subscriber.close();
         } catch (InterruptedException e) {
            logger.warn("Closing NATS subscriber raised an exception", e);
         }
      }
   }

   private void intializeNATSClient() throws Exception {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();
      String endpointBrokerUrl = matcher.group("brokerUrl");
      endpointTopic = matcher.group("topic");

      Options.Builder optionsBuilder = new Options.Builder().server(endpointBrokerUrl).maxReconnects(10);

      if (specification.getSecret() != null) {
         if (specification.getSecret().getUsername() != null && specification.getSecret().getPassword() != null) {
            logger.debug("Adding username/password authentication from secret " + specification.getSecret().getName());
            optionsBuilder.userInfo(specification.getSecret().getUsername(), specification.getSecret().getPassword());
         } else if (specification.getSecret().getToken() != null) {
            logger.debug("Adding token authentication from secret " + specification.getSecret().getName());
            optionsBuilder.token(specification.getSecret().getToken().toCharArray());
         }
      }
      subscriber = Nats.connect(optionsBuilder.build());
   }
}
