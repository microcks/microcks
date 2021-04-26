package io.github.microcks.minion.async.producer;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

public abstract class BindingProducerManager {
   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   
   @ConfigProperty(name = "minion.namespace.style", defaultValue="prefixed")
   String namespaceStyle;   
   /**
    * Template method ensures all required operations to publish messages are called
    * @param definition
    */
   public final void publishMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String topic = this.getTopicName(definition, eventMessage);
         this.publishOne(definition, eventMessage, topic);
      }
   }
   /**
    * Provide a binding specific method to publish one message
    * @param message 
    * @param topic
    */
   protected abstract void publishOne(AsyncMockDefinition definition, EventMessage eventMessage, String topic);
   
   /**
    * Post processing method to transform a generic topic (channel) string to a binding specific topic\
    * This default implementation returns the topic as supplied.
    * @param topic
    * @return transformed topic appropriate for the binding
    */
   protected String transformToBindingSpecificTopicName(String topic) {
      return topic;
   }
   
   public final String getTopicName(AsyncMockDefinition definition, EventMessage eventMessage) {
      logger.infof("AsyncAPI Operation %s", definition.getOperation().getName());
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");
      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "");
      // Produce operation name part of topic name.
      String operationName = definition.getOperation().getName();
      if (operationName.startsWith("SUBSCRIBE ") || operationName.startsWith("PUBLISH ")) {
         operationName = operationName.substring(operationName.indexOf(" ") + 1);
      }

      // replace the parts
      operationName = this.replacePartPlaceholders(eventMessage, operationName);
      
      // post process for binding specific transformation of topic
      operationName = this.transformToBindingSpecificTopicName(operationName);

      // Aggregate the 3 parts using '_' as delimiter.
      String topic = null;
      if ("prefixed".equalsIgnoreCase(this.namespaceStyle)) {
         topic = String.join("-", serviceName, versionName, operationName); 
      } else if ("bare".equalsIgnoreCase(this.namespaceStyle)) {
         topic = operationName;
      }
      return topic;
   }
   
   protected String replacePartPlaceholders(EventMessage eventMessage, String operationName) {
      Map<String, String> parameters = new HashMap<String, String>();
      String partsCriteria = eventMessage.getDispatchCriteria();
      if (partsCriteria != null && !partsCriteria.isBlank()) {
         String[] criterion = partsCriteria.split("/");
        
         for (String criteria : criterion) {
            if (criteria != null && !criteria.isBlank() && criteria.contains("=")) {
               String[] element = criteria.split("=");
               parameters.put(element[0], element[1]);
            }
         }
      }
      return URIBuilder.buildURIFromPattern(operationName, parameters);
   }
   /**
    * Render event message content from definition applying template rendering if required.
    */
   protected String renderEventMessageContent(EventMessage eventMessage) {
      String content = eventMessage.getContent();
      if (content.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         logger.debug("EventMessage contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

         try {
            content = engine.getValue(content);
         } catch (Throwable t) {
            logger.error("Failing at evaluating template " + content, t);
         }
      }
      return content;
   }
}
