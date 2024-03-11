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

import io.github.microcks.minion.async.AsyncTestSpecification;
import org.apache.http.ssl.SSLContexts;
import org.jboss.logging.Logger;

import javax.net.ssl.SSLContext;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a WebSocket endpoint. Endpoint URL should be
 * specified using the following form: <code>ws://{wsHost[:port]}/{channel}[?option1=value1&amp;option2=value2]</code>.
 * Channel may be empty if connecting to the root context of the WebSocket server.
 * @author laurent
 */
public class WebSocketMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "ws://(?<wsHost>[^:]+(:\\d+)?)/(?<channel>.*)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   private File trustStore;

   private AsyncTestSpecification specification;

   private Session session;

   /**
    * Create a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public WebSocketMessageConsumptionTask(AsyncTestSpecification testSpecification) {
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
      WebSocketClient client = new WebSocketClient();
      try {
         WebSocketContainer container = ContainerProvider.getWebSocketContainer();

         if (specification.getSecret() != null && specification.getSecret().getCaCertPem() != null) {
            // We neet to create a Truststore holding secret certificxate.
            trustStore = ConsumptionTaskCommons.installBrokerCertificate(specification);

            SSLContext sslContext = SSLContexts.custom()
                  .loadTrustMaterial(trustStore, ConsumptionTaskCommons.TRUSTSTORE_PASSWORD.toCharArray(), null)
                  .build();

            // Then configure the Client Endpoint using a property specific to Undertow impl (from Quarkus).
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
            config.getUserProperties().put("io.undertow.websocket.SSL_CONTEXT", sslContext);

            // Then connect replacing ws:// by wss://
            session = container.connectToServer(client, config,
                  URI.create(specification.getEndpointUrl().replace("ws://", "wss://")));
         } else {
            // Simply connect to plain text WebSocket...
            session = container.connectToServer(client, URI.create(specification.getEndpointUrl()));
         }
      } catch (Exception e) {
         logger.errorf("Connection error while try to reach {%s}", specification.getEndpointUrl());
         throw e;
      }

      Thread.sleep(specification.getTimeoutMS());
      if (session != null) {
         try {
            session.close();
         } catch (Exception e) {
            logger.info("Exception while closing the WebSocket session: " + e.getMessage());
         }
      }
      return client.getMessages();
   }

   /**
    * Close the resources used by this task. Namely the WS client session and the optionally created truststore holding
    * server client SSL credentials.
    * @throws IOException should not happen.
    */
   @Override
   public void close() throws IOException {
      if (session != null) {
         try {
            session.close();
         } catch (Exception e) {
            logger.warn("Closing WebSocket session raised an exception", e);
         }
      }
      if (trustStore != null && trustStore.exists()) {
         trustStore.delete();
      }
   }
}
