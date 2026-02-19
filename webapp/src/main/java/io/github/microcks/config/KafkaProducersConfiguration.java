package io.github.microcks.config;

import io.github.microcks.event.AsyncAPITriggerCommand;
import io.github.microcks.event.ServiceViewChangeEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(value = "async-api.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducersConfiguration {

   private final KafkaMultipleProducersProperties kafkaMultipleProducersProperties;

   public KafkaProducersConfiguration(KafkaMultipleProducersProperties kafkaMultipleProducersProperties) {
      this.kafkaMultipleProducersProperties = kafkaMultipleProducersProperties;
   }

   @Bean("serviceViewChangesKafkaTemplate")
   public KafkaTemplate<String, ServiceViewChangeEvent> serviceViewChangesKafkaTemplate() {
      return new KafkaTemplate<>(producerFactory("service-changes", ServiceViewChangeEvent.class));
   }

   @Bean("asyncAPITriggerCommandKafkaTemplate")
   public KafkaTemplate<String, AsyncAPITriggerCommand> asyncAPITriggerCommandKafkaTemplate() {
      return new KafkaTemplate<>(producerFactory("asyncapi-triggers", AsyncAPITriggerCommand.class));
   }

   private <T> ProducerFactory<String, T> producerFactory(String producerName, Class<T> eventTypeClazz) {
      Map<String, Object> properties = new HashMap<>(kafkaMultipleProducersProperties.buildCommonProperties());
      KafkaProperties.Producer producerProps = kafkaMultipleProducersProperties.getProducers().get(producerName);
      if (producerProps != null) {
         properties.putAll(producerProps.buildProperties(null));
      }
      return new DefaultKafkaProducerFactory<>(properties);
   }
}
