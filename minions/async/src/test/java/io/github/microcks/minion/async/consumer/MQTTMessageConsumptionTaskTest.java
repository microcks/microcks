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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for MQTTMessageConsumptionTask.
 * @author laurent
 */
class MQTTMessageConsumptionTaskTest {

   @Test
   void testAcceptEndpoint() {
      assertTrue(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost/testTopic"));

      assertTrue(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost:1883/testTopic"));

      assertTrue(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost:1883/topic/with/path/elements"));

      assertTrue(
            MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost:1883/topic/with/path/elements?option1=value1"));

      assertTrue(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost/testTopic/option1=value1"));

      assertTrue(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost:1883/testTopic/option1=value1"));

      assertTrue(MQTTMessageConsumptionTask.acceptEndpoint(
            "mqtt://my-cluster-activemq.apps.cluster-943b.943b.example.com:1883/testTopic/option1=value1"));
   }

   @Test
   void testAcceptEndpointFailures() {
      assertFalse(MQTTMessageConsumptionTask.acceptEndpoint("localhost:1883/testTopic"));

      assertFalse(MQTTMessageConsumptionTask.acceptEndpoint("ssl://localhost:1883/testTopic"));

      assertFalse(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost"));

      assertFalse(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost:1883"));

      assertFalse(MQTTMessageConsumptionTask.acceptEndpoint("mqtt://localhost:port/testTopic"));
   }

   /*
    * @Test public void testTLSConnection() { try { MqttConnectOptions connectOptions = new MqttConnectOptions();
    * connectOptions.setAutomaticReconnect(false); connectOptions.setCleanSession(true);
    * connectOptions.setConnectionTimeout(10);
    * 
    * connectOptions.setUserName("admin"); connectOptions.setPassword("admin".toCharArray());
    * 
    * AsyncTestSpecification specification = new AsyncTestSpecification(); Secret secret = new Secret();
    * secret.setCaCertPem("-----BEGIN CERTIFICATE-----\n" +
    * "MIIDTTCCAjWgAwIBAgIEZ3f4vzANBgkqhkiG9w0BAQsFADBXMQswCQYDVQQGEwJG\n" +
    * "UjEPMA0GA1UEBxMGTGVNYW5zMRMwEQYDVQQKEwpyZWRoYXQuY29tMQ4wDAYDVQQL\n" +
    * "EwVTYWxlczESMBAGA1UEAxMJbGJyb3Vkb3V4MB4XDTIwMTIxNjA5MDQ1OFoXDTIx\n" +
    * "MDMxNjA5MDQ1OFowVzELMAkGA1UEBhMCRlIxDzANBgNVBAcTBkxlTWFuczETMBEG\n" +
    * "A1UEChMKcmVkaGF0LmNvbTEOMAwGA1UECxMFU2FsZXMxEjAQBgNVBAMTCWxicm91\n" +
    * "ZG91eDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMv+ZNhj1P6MF7i/\n" +
    * "6Vdid9GNNi3q2CKgTKLPMUQjJON8Fn908WLuwsATqZILwpKUGl3Fl7lYDETaAu0h\n" +
    * "viaRol4TFok8yU9i1YSt/lP7mNhnoi+/GprxlyTIifWmm/PVApzyyT+j2jUhQnuh\n" +
    * "mizikQwfrGC+Ma3irJb7/lDqjU1LGNYHdOe+zGrTbVY0KavnMk8cD/pJeOtFNOU2\n" +
    * "HvWax2Iah51qoYBVJfz3+yQdMbTG2GYjATaoEqF7HRrfxlahVTWus5giSszwNfGG\n" +
    * "SoeTNVJbLZzMVOoNhQ7P98Ft0jH3ger9rT+5yDrFeqdsW6lo0uN+DZvBjYaHIN6/\n" +
    * "rqVF9wECAwEAAaMhMB8wHQYDVR0OBBYEFK+gMMjv7ATBlDiq497auPf6sb0JMA0G\n" +
    * "CSqGSIb3DQEBCwUAA4IBAQCap2OWWuhVyo2v55voV83aa3TLPMxWIQGEcKIFKSQy\n" +
    * "orA+l6gvVF920XOS7/m/RU3NBxHKetk+/UnvjyxbPJGGyJFLqGfp1loT9m0RbzU6\n" +
    * "M7iY0DuXja08gxlVO0tpaquNfMgasH3JoAYGS8f3DpHEOExb1gHiumIXEzlcNutV\n" +
    * "Zn/ZSR+EdCJaDvC/3cLmVR07pFmGH8JM5xLWxh7wuYk9bHEEoAgVTsFk1vp+MUUU\n" +
    * "KNEUjdRBSJbPKLIJKPlWWX5srefTIypUJrHys3Sxccpzlk064QIY5/IiaFk0+vXs\n" +
    * "cPDGdmeplII2oAxj1qrAIFtaUZyyhDOmFFpQYm27+bYh\n" + "-----END CERTIFICATE-----"); specification.setSecret(secret);
    * File trustStore = ConsumptionTaskCommons.installBrokerCertificate(specification);
    * System.err.println("Using trustStore: " + trustStore.getAbsolutePath());
    * 
    * Properties sslProperties = new Properties(); sslProperties.put("com.ibm.ssl.trustStore",
    * trustStore.getAbsolutePath()); sslProperties.put("com.ibm.ssl.trustStorePassword",
    * ConsumptionTaskCommons.TRUSTSTORE_PASSWORD); sslProperties.put("com.ibm.ssl.trustStoreType", "JKS");
    * connectOptions.setSSLProperties(sslProperties);
    * 
    * IMqttClient subscriber = new
    * MqttClient("ssl://artemis-my-acceptor-0-svc-rte-microcks.apps.cluster-87b8.87b8.example.opentlc.com:443",
    * "microcks-async-minion-test-" + System.currentTimeMillis()); subscriber.connect(connectOptions);
    * 
    * // Start subscribing to the server endpoint topic. subscriber.subscribe("streetlights-event-lighting-measured",
    * (topic, mqttMessage) -> { System.err.println("Received a new MQTT Message: " + new
    * String(mqttMessage.getPayload())); });
    * 
    * Thread.sleep(10000L);
    * 
    * } catch (Throwable t) { t.printStackTrace(); fail(); } }
    */
}
