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
package io.github.microcks.minion.async;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.UnidirectionalEvent;

import io.github.microcks.minion.async.client.ConnectorException;
import io.github.microcks.minion.async.client.KeycloakConfig;
import io.github.microcks.minion.async.client.KeycloakConnector;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;

import io.quarkus.runtime.StartupEvent;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A Minion App for dealing with Async message mocks.
 * @author laurent
 */
@ApplicationScoped
public class AsyncMinionApp {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private static final int SERVICES_FETCH_SIZE = 30;

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   @ConfigProperty(name = "minion.restricted-frequencies")
   Long[] restrictedFrequencies;

   @ConfigProperty(name = "keycloak.auth.url", defaultValue = "")
   Optional<String> keycloakAuthURL;

   final MicrocksAPIConnector microcksAPIConnector;
   final KeycloakConnector keycloakConnector;
   final AsyncMockRepository mockRepository;
   final SchemaRegistry schemaRegistry;
   final ProducerScheduler producerScheduler;

   /**
    * Build a new instance of the AsyncMinionApp with required dependencies.
    * @param microcksAPIConnector to access Microcks API
    * @param keycloakConnector    to access Keycloak server for authentication
    * @param mockRepository       to store and retrieve mock definitions
    * @param schemaRegistry       to store and retrieve schema definitions
    * @param producerScheduler    to initiate producer jobs
    */
   public AsyncMinionApp(@RestClient MicrocksAPIConnector microcksAPIConnector, KeycloakConnector keycloakConnector,
         AsyncMockRepository mockRepository, SchemaRegistry schemaRegistry, ProducerScheduler producerScheduler) {
      this.microcksAPIConnector = microcksAPIConnector;
      this.keycloakConnector = keycloakConnector;
      this.mockRepository = mockRepository;
      this.schemaRegistry = schemaRegistry;
      this.producerScheduler = producerScheduler;
   }


   /** Application startup method. */
   void onStart(@Observes StartupEvent ev) {

      // We need to retrieve Keycloak server from Microcks config.
      KeycloakConfig config = microcksAPIConnector.getKeycloakConfig();
      logger.infof("Microcks Keycloak server url {%s} and realm {%s}", config.getAuthServerUrl(), config.getRealm());

      String keycloakEndpoint = config.getAuthServerUrl() + "/realms/" + config.getRealm()
            + "/protocol/openid-connect/token";
      if (!keycloakAuthURL.isEmpty() && keycloakAuthURL.get().length() > 0) {
         logger.infof("Use locally defined Keycloak Auth URL: %s", keycloakAuthURL);
         keycloakEndpoint = keycloakAuthURL.get() + "/realms/" + config.getRealm() + "/protocol/openid-connect/token";
      }

      try {
         // First retrieve an authentication token before fetching async messages to publish.
         String oauthToken;
         if (config.isEnabled()) {
            // We've got a full Keycloak config, attempt an authent.
            oauthToken = keycloakConnector.connectAndGetOAuthToken(keycloakEndpoint);
            logger.info("Authentication to Keycloak server succeed!");
         } else {
            // No realm config, probably a dev mode - use a fake token.
            oauthToken = "<anonymous-admin-token>";
            logger.info("Keycloak protection is not enabled, using a fake token");
         }

         int page = 0;
         boolean fetchServices = true;
         while (fetchServices) {
            List<Service> services = microcksAPIConnector.listServices("Bearer " + oauthToken, page,
                  SERVICES_FETCH_SIZE);
            for (Service service : services) {
               logger.debug("Found service " + service.getName() + " - " + service.getVersion());

               if (service.getType().equals(ServiceType.EVENT) || service.getType().equals(ServiceType.GENERIC_EVENT)) {
                  // Find the operations matching this minion constraints..
                  List<Operation> operations = service.getOperations().stream()
                        .filter(o -> Arrays.asList(restrictedFrequencies).contains(o.getDefaultDelay())).filter(o -> o
                              .getBindings().keySet().stream().anyMatch(Arrays.asList(supportedBindings)::contains))
                        .toList();

                  if (!operations.isEmpty()) {
                     logger.info("Found " + operations.size() + " candidate operations in " + service.getName() + " - "
                           + service.getVersion());
                     ServiceView serviceView = microcksAPIConnector.getService("Bearer " + oauthToken, service.getId(),
                           true);

                     for (Operation operation : operations) {
                        AsyncMockDefinition mockDefinition = new AsyncMockDefinition(serviceView.getService(),
                              operation,
                              serviceView.getMessagesMap().get(operation.getName()).stream()
                                    .filter(UnidirectionalEvent.class::isInstance)
                                    .map(e -> ((UnidirectionalEvent) e).getEventMessage()).toList());
                        mockRepository.storeMockDefinition(mockDefinition);
                        schemaRegistry.updateRegistryForService(mockDefinition.getOwnerService());
                     }
                  }
               }
            }
            if (services.size() < SERVICES_FETCH_SIZE) {
               fetchServices = false;
            }
            page++;
         }

         logger.info("Starting scheduling of all producer jobs...");
         producerScheduler.scheduleAllProducerJobs();

      } catch (ConnectorException ce) {
         logger.error("Cannot authenticate to Keycloak server and thus enable to call Microcks API"
               + " to get Async APIs to mocks...", ce);
         throw new RuntimeException("Unable to start the Minion due to connection exception");
      } catch (IOException ioe) {
         logger.error("IOException while communicating with Keycloak or Microcks API", ioe);
         throw new RuntimeException("Unable to start the Minion due to IO exception");
      }
   }
}
