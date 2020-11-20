package io.github.microcks.async.consumer;

import io.github.microcks.minion.async.AsyncTestSpecification;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import org.junit.jupiter.api.Test;

public class MQTTMessageConsumptionTaskTest {

   @Test
   public void testConsumeMessages() {
      try {
         AsyncTestSpecification spec = new AsyncTestSpecification();
         spec.setEndpointUrl("mqtt://localhost:1883/streetlights-event-lighting-measured");

         MqttConnectOptions options = new MqttConnectOptions();
         options.setUserName("microcks");
         options.setPassword("microcks".toCharArray());

         options.setConnectionTimeout(10);
         IMqttClient subscriber = new MqttClient("tcp://localhost:1883", "microcks-async-minion");
         subscriber.connect(options);

         // Start subscribing to the server endpoint topic.
         subscriber.subscribe("streetlights-event-lighting-measured", (topic, mqttMessage) -> {
            System.err.println("Received a new MQTT Message: " + new String(mqttMessage.getPayload()));
         });

         //Thread.sleep(10000L);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

}
