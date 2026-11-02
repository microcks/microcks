package io.github.microcks.minion.async.handler;

import io.github.microcks.domain.Binding;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.producer.GooglePubSubProducerManager;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Factory for creating Google PubSub request-reply handlers.
 * @author laurent
 */
@ApplicationScoped
public class GooglePubSubRequestReplyHandlerFactory {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   Config config;

   final GooglePubSubProducerManager producerManager;
   final SchemaRegistry schemaRegistry;

   /**
    * Create a new factory with required dependencies.
    *
    * @param producerManager The Google PubSub producer manager for sending replies
    * @param schemaRegistry  The schema registry for message schemas
    */
   public GooglePubSubRequestReplyHandlerFactory(GooglePubSubProducerManager producerManager,
         SchemaRegistry schemaRegistry) {
      this.producerManager = producerManager;
      this.schemaRegistry = schemaRegistry;
   }

   /**
    * Create a Google PubSub request-reply handler for the given mock definition.
    *
    * @param definition The mock definition to create a handler for
    * @param binding    The PubSub binding information
    * @return A new GooglePubSubRequestReplyHandler
    */
   public GooglePubSubRequestReplyHandler createHandler(AsyncMockDefinition definition, Binding binding) {
      logger.debugf("Creating Google PubSub request-reply handler for %s - %s",
            definition.getOwnerService().getName() + ":" + definition.getOwnerService().getVersion(),
            definition.getOperation().getName());

      return new GooglePubSubRequestReplyHandler(definition, binding, producerManager, schemaRegistry, config);
   }
}
