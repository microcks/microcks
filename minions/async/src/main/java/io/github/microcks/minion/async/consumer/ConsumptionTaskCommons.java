package io.github.microcks.minion.async.consumer;

import io.github.microcks.minion.async.AsyncTestSpecification;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 *
 */
public class ConsumptionTaskCommons {

   /** Constant representing the header line in a custom CA Cert in PEM format. */
   private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
   /** Constant representing the footer line in a custom CA Cert in PEM format. */
   private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
   /** The password that is used when generating a custom truststore. */
   public static final String TRUSTSTORE_PASSWORD = "password";

   /**
    * Install broker custom certificate into a truststore file.
    * @param specification The specification holding secret information
    * @return A newly created trustStore file created as temporary file.
    */
   public static File installBrokerCertificate(AsyncTestSpecification specification) throws Exception {
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

      File trustStore = File.createTempFile("microcks-truststore-" + System.currentTimeMillis(), ".jks");

      try (FileOutputStream fos = new FileOutputStream(trustStore)) {
         ks.store(fos, password);
      }

      return trustStore;
   }
}
