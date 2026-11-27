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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a utility class holding commons routines for MessageConsumptionTask implementations.
 * @author laurent
 */
public class ConsumptionTaskCommons {

   /** Constant representing the header line in a custom CA Cert in PEM format. */
   private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
   /** Constant representing the footer line in a custom CA Cert in PEM format. */
   private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
   /** The password that is used when generating a custom truststore. */
   public static final String TRUSTSTORE_PASSWORD = "password";

   private ConsumptionTaskCommons() {
      // Private constructor to hide the implicit public one.
   }

   /**
    * Install broker custom certificate into a truststore file.
    * @param specification The specification holding secret information
    * @return A newly created trustStore file created as temporary file.
    * @throws Exception in case of IO exception while decoding secret or writing the truststore file.
    */
   public static File installBrokerCertificate(AsyncTestSpecification specification) throws Exception {
      String caCertPem = specification.getSecret().getCaCertPem();

      // First compute a stripped PEM certificate and decode it from base64.
      String strippedPem = caCertPem.replaceAll(BEGIN_CERTIFICATE, "").replaceAll(END_CERTIFICATE, "");
      InputStream is = new ByteArrayInputStream(org.apache.commons.codec.binary.Base64.decodeBase64(strippedPem));

      // Generate a new x509 certificate from the stripped decoded pem.
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate caCert = (X509Certificate) cf.generateCertificate(is);

      // Create a new TrustStore using KeyStore API.
      char[] password = TRUSTSTORE_PASSWORD.toCharArray();
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null, password);
      ks.setCertificateEntry("root", caCert);

      File trustStore = File.createTempFile("microcks-truststore-" + System.currentTimeMillis(), ".jks");

      try (FileOutputStream fos = new FileOutputStream(trustStore)) {
         ks.store(fos, password);
      }

      return trustStore;
   }

   /**
    * Initialize options map from options string found in Endpoint URL.
    * @param options A string of options having the form: option1=value1&amp;option2=value2
    * @return A Map of options supplied in endpoint url.
    */
   public static Map<String, String> initializeOptionsMap(String options) {
      Map<String, String> optionsMap = new HashMap<>();
      String[] keyValuePairs = options.split("&");
      for (String keyValuePair : keyValuePairs) {
         String[] keyValue = keyValuePair.split("=");
         if (keyValue.length > 1) {
            optionsMap.put(keyValue[0], keyValue[1]);
         }
      }
      return optionsMap;
   }
}
