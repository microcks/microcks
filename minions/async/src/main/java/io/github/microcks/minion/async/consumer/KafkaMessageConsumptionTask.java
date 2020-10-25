/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.minion.async.consumer;

import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncTestSpecification;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jboss.logging.Logger;

import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a topic on an Apache Kafka Broker.
 * Endpoint URL should be specified using the following form: <code>kafka://{brokerhost[:port]}/{topic}[?option1=value1&amp;option2=value2]</code>
 * @author laurent
 */
public class KafkaMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating aceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "kafka://(?<brokerUrl>[^:]+(:\\d+)?)/(?<topic>.+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   /** Constant representing the header line in a custom CA Cert in PEM format. */
   private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
   /** Constant representing the footer line in a custom CA Cert in PEM format. */
   private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
   /** The password that isused when generating a custom truststore. */
   private static final String TRUSTSTORE_PASSWORD = "password";

   private File trustStore;

   private AsyncTestSpecification specification;

   private KafkaConsumer<String, String> consumer;


   /**
    * Create a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public KafkaMessageConsumptionTask(AsyncTestSpecification testSpecification) {
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
      if (consumer == null) {
         initializeKafkaConsumer();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      // Start polling consumer for records.
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(specification.getTimeoutMS()));

      for (ConsumerRecord<String, String> record : records) {
         // Build a ConsumedMessage from Kafka record.
         ConsumedMessage message = new ConsumedMessage();
         message.setReceivedAt(System.currentTimeMillis());
         message.setHeaders(buildHeaders(record.headers()));
         message.setPayload(record.value().getBytes("UTF-8"));
         messages.add(message);
      }
      // Close the consumer before returning results.
      consumer.close();
      return messages;
   }

   /**
    * Close the resources used by this task. Namely the Kafka consumer and
    * the optionally created truststore holding Kafka client SSL credentials.
    * @throws IOException should not happen.
    */
   @Override
   public void close() throws IOException {
      if (consumer != null) {
         consumer.close();
      }
      if (trustStore != null && trustStore.exists()) {
         trustStore.delete();
      }
   }

   /** Initialize Kafka consumer from built properties and subscribe to target topic. */
   private void initializeKafkaConsumer() {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl());
      // Call matcher.find() to be abled to use named expressions.
      matcher.find();
      String endpointBrokerUrl = matcher.group("brokerUrl");
      String endpointTopic = matcher.group("topic");
      String options = matcher.group("options");

      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, endpointBrokerUrl);

      // Generate a unique GroupID for no collision with previous or other consumers.
      props.put(ConsumerConfig.GROUP_ID_CONFIG, specification.getTestResultId() + "-" + System.currentTimeMillis());
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-test");
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

      // Only retrieve incoming messages and do not persist offset.
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

      if (specification.getSecret() != null && specification.getSecret().getCaCertPem() != null) {
         try {
            // Because Kafka Java client does not support any other sources for SSL configuration,
            // we need to create a Truststore holding the secret certificate and credentials. See below:
            // https://cwiki.apache.org/confluence/display/KAFKA/KIP-486%3A+Support+custom+way+to+load+KeyStore+and+TrustStore
            installBrokerCertificate();

            // Then we have to add SSL specific properties.
            props.put("security.protocol", "SSL");
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStore.getAbsolutePath());
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, TRUSTSTORE_PASSWORD);
         } catch (Exception e) {
            logger.error("Exception while installing custom truststore: " + e.getMessage());
         }
      }

      // Create the consumer from properties and subscribe to given topis.
      consumer = new KafkaConsumer<>(props);
      consumer.subscribe(Arrays.asList(endpointTopic));
   }

   /** Install broker custom certificate into a truststore file. */
   private void installBrokerCertificate() throws Exception {
      String caCertPem = specification.getSecret().getCaCertPem();

      // First compute a stripped PEM certificate and decode it from base64.
      String strippedPem = caCertPem.replaceAll(BEGIN_CERTIFICATE, "")
            .replaceAll(END_CERTIFICATE, "");
      InputStream is = new ByteArrayInputStream(org.apache.commons.codec.binary.Base64.decodeBase64(strippedPem));

      // Generate a new x509 certificate from the stripped decoded pem.
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate caCert = (X509Certificate)cf.generateCertificate(is);

      // Create a new TrustStore using KeyStore API.
      char[] password = TRUSTSTORE_PASSWORD.toCharArray();
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null, password);
      ks.setCertificateEntry("root", caCert);

      trustStore = File.createTempFile("microcks-truststore-" + System.currentTimeMillis(), ".jks");

      try (FileOutputStream fos = new FileOutputStream(trustStore)) {
         ks.store(fos, password);
      }
   }

   /** Build set of Microcks headers from Kafka headers. */
   private Set<Header> buildHeaders(Headers headers) {
      if (headers == null || !headers.iterator().hasNext()) {
         return null;
      }
      Set<Header> results = new TreeSet<>();
      Iterator<org.apache.kafka.common.header.Header> headersIterator = headers.iterator();
      while (headersIterator.hasNext()) {
         org.apache.kafka.common.header.Header header = headersIterator.next();
         Header result = new Header();
         result.setName(header.key());
         result.setValues(Stream.of(new String(header.value())).collect(Collectors.toSet()));
      }
      return results;
   }
}
