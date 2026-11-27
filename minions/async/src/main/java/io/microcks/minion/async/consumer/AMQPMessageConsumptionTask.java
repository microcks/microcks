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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.jboss.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a queue on an RabbitMQ 3.x Server. Endpoint
 * URL should be specified using the following form:
 * <code>amqp://{brokerhost[:port]}[/{virtualHost}]/{type}/{destination}[?option1=value1&amp;option2=value2]</code>
 * @author laurent
 */
public class AMQPMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "amqp://(?<brokerUrl>[^:]+(:\\d+)?)/(?<virtualHost>[a-zA-Z0-9-_]+/)?(?<type>[q|d|f|t|h])/(?<destination>[^?]+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   /** Constant representing a queue destination type in endpoint URL. */
   public static final String QUEUE_TYPE = "q";
   /** Constant representing a direct exchange destination type in endpoint URL. */
   public static final String DIRECT_TYPE = "d";
   /** Constant representing a fanout exchange destination type in endpoint URL. */
   public static final String FANOUT_TYPE = "f";
   /** Constant representing a topic exchange destination type in endpoint URL. */
   public static final String TOPIC_TYPE = "t";
   /** Constant representing a headers exchange destination type in endpoint URL. */
   public static final String HEADERS_TYPE = "h";

   /** The endpoint URL option representing routing key. */
   public static final String ROUTING_KEY_OPTION = "routingKey";
   /** The endpoint URL option representing durable property. */
   public static final String DURABLE_OPTION = "durable";

   private File trustStore;

   private final AsyncTestSpecification specification;

   protected Map<String, String> optionsMap;

   private Connection connection;

   private String virtualHost;

   private String destinationType;

   private String destinationName;

   private String options;

   /**
    * Create a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public AMQPMessageConsumptionTask(AsyncTestSpecification testSpecification) {
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
      if (connection == null) {
         initializeAMQPConnection();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      try (Channel channel = connection.createChannel()) {
         String queueName = destinationName;

         if (!destinationType.equals(QUEUE_TYPE)) {
            boolean durable = false;
            if (optionsMap != null && optionsMap.containsKey(DURABLE_OPTION)) {
               durable = Boolean.parseBoolean(optionsMap.get(DURABLE_OPTION));
            }
            String routingKey = "#";
            if (optionsMap != null && optionsMap.containsKey(ROUTING_KEY_OPTION)) {
               routingKey = optionsMap.get(ROUTING_KEY_OPTION);
            }

            switch (destinationType) {
               case DIRECT_TYPE:
                  channel.exchangeDeclare(destinationName, "direct", durable);
                  queueName = channel.queueDeclare().getQueue();
                  channel.queueBind(queueName, destinationName, routingKey);
                  break;
               case HEADERS_TYPE:
                  channel.exchangeDeclare(destinationName, "headers", durable);
                  queueName = channel.queueDeclare().getQueue();
                  // Specify any header if specified otherwise default to routing key.
                  Map<String, Object> bindingArgs = buildHeaderArgs();
                  if (bindingArgs != null && !bindingArgs.isEmpty()) {
                     bindingArgs.put("x-match", "any");
                     channel.queueBind(queueName, destinationName, "", bindingArgs);
                  } else {
                     channel.queueBind(queueName, destinationName, routingKey);
                  }
                  break;
               case FANOUT_TYPE:
                  channel.exchangeDeclare(destinationName, "fanout", durable);
                  queueName = channel.queueDeclare().getQueue();
                  channel.queueBind(queueName, destinationName, "");
                  break;
               case TOPIC_TYPE:
               default:
                  channel.exchangeDeclare(destinationName, "topic", durable);
                  queueName = channel.queueDeclare().getQueue();
                  channel.queueBind(queueName, destinationName, routingKey);
                  break;
            }
         }

         String consumerTag = channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                  byte[] body) throws IOException {
               logger.infof("Received a new AMQP Message: %s", new String(body));
               // Build a ConsumedMessage from AMQP message.
               ConsumedMessage message = new ConsumedMessage();
               message.setReceivedAt(System.currentTimeMillis());
               message.setHeaders(buildHeaders(properties.getHeaders()));
               message.setPayload(body);
               messages.add(message);

               channel.basicAck(envelope.getDeliveryTag(), false);
            }
         });

         Thread.sleep(specification.getTimeoutMS());

         channel.basicCancel(consumerTag);
      }

      return messages;
   }

   /**
    * Close the resources used by this task. Namely the AMQP connection and the optionally created truststore holding
    * server client SSL credentials.
    * @throws IOException should not happen.
    */
   @Override
   public void close() throws IOException {
      if (connection != null) {
         try {
            connection.close();
         } catch (IOException ioe) {
            logger.warn("Closing AMQP connection raised an exception", ioe);
         }
      }
      if (trustStore != null && trustStore.exists()) {
         Files.delete(trustStore.toPath());
      }
   }

   /** Initialize AMQP connection from test properties. */
   private void initializeAMQPConnection() throws Exception {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();
      String endpointBrokerUrl = matcher.group("brokerUrl");
      virtualHost = matcher.group("virtualHost");
      destinationType = matcher.group("type");
      destinationName = matcher.group("destination");
      options = matcher.group("options");

      // Parse options if specified.
      if (options != null && !options.isBlank()) {
         optionsMap = ConsumptionTaskCommons.initializeOptionsMap(options);
      }

      ConnectionFactory factory = new ConnectionFactory();
      if (endpointBrokerUrl.contains(":")) {
         String[] serverAndPort = endpointBrokerUrl.split(":");
         factory.setHost(serverAndPort[0]);
         factory.setPort(Integer.parseInt(serverAndPort[1]));
      } else {
         factory.setHost(endpointBrokerUrl);
      }
      if (virtualHost != null && !virtualHost.isEmpty()) {
         factory.setVirtualHost(virtualHost);
      }

      if (specification.getSecret() != null) {
         if (specification.getSecret().getUsername() != null && specification.getSecret().getPassword() != null) {
            logger.debug("Adding username/password authentication from secret " + specification.getSecret().getName());
            factory.setUsername(specification.getSecret().getUsername());
            factory.setPassword(specification.getSecret().getPassword());
         }

         if (specification.getSecret().getCaCertPem() != null) {
            logger.debug("Installing a broker certificate from secret " + specification.getSecret().getName());
            trustStore = ConsumptionTaskCommons.installBrokerCertificate(specification);

            // Load the truststore into a ssl context as explained here:
            // https://www.rabbitmq.com/ssl.html#java-client
            KeyStore tks = KeyStore.getInstance("JKS");
            tks.load(new FileInputStream(trustStore), ConsumptionTaskCommons.TRUSTSTORE_PASSWORD.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(tks);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, tmf.getTrustManagers(), null);

            factory.useSslProtocol(sslContext);
            factory.enableHostnameVerification();
         }
      }
      connection = factory.newConnection();
   }

   /** Build the map of headers as binding arguments for headers type exchange. */
   private Map<String, Object> buildHeaderArgs() {
      if (optionsMap != null) {
         Map<String, Object> results = new HashMap<>();
         // ?h.header1=value1&h.header2=value2
         for (String option : optionsMap.keySet()) {
            if (option.startsWith(HEADERS_TYPE + ".")) {
               String headerName = option.substring(HEADERS_TYPE.length() + 1);
               String headerValue = optionsMap.get(option);
               results.put(headerName, headerValue);
            }
         }
         return results;
      }
      return null;
   }

   /** Build set of Microcks headers from RabbitMQ headers. */
   private Set<Header> buildHeaders(Map<String, Object> headers) {
      if (headers == null || headers.isEmpty()) {
         return null;
      }
      Set<Header> results = new HashSet<>();
      for (String key : headers.keySet()) {
         Header result = new Header();
         result.setName(key);
         result.setValues(Set.of(headers.get(key).toString()));
         results.add(result);
      }
      return results;
   }
}
