package io.github.microcks.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "kafka")
public class KafkaMultipleProducersProperties {

   private List<String> bootstrapServers;
   private Map<String, Object> properties = new HashMap<>();
   private Map<String, KafkaProperties.Producer> producers = new HashMap<>();
   private KafkaProperties.Ssl ssl = new KafkaProperties.Ssl();


   public List<String> getBootstrapServers() {
      return bootstrapServers;
   }

   public void setBootstrapServers(List<String> bootstrapServers) {
      this.bootstrapServers = bootstrapServers;
   }

   public Map<String, Object> getProperties() {
      return properties;
   }

   public void setProperties(Map<String, Object> properties) {
      this.properties = properties;
   }

   public Map<String, KafkaProperties.Producer> getProducers() {
      return producers;
   }

   public void setProducers(Map<String, KafkaProperties.Producer> producers) {
      this.producers = producers;
   }

   public KafkaProperties.Ssl getSsl() {
      return ssl;
   }

   public void setSsl(KafkaProperties.Ssl ssl) {
      this.ssl = ssl;
   }

   public Map<String, Object> buildCommonProperties() {
      HashMap<String, Object> commonProperties = new HashMap<>();
      commonProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      return commonProperties;
   }
}
