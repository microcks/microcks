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
package io.github.microcks.util;

import io.github.microcks.domain.Secret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * This is a utility class for accessing HTTP content using diverse security authentication mechanisms and output
 * formats
 *
 * @author laurent
 */
public class HTTPDownloader {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(HTTPDownloader.class);

   /** Constant representing the header line in a custom CA Cert in PEM format. */
   private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
   /** Constant representing the footer line in a custom CA Cert in PEM format. */
   private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

   private HTTPDownloader() {
      // Private constrcutor to hide the implicit public one.
   }

   /**
    * Manage the retrieval of Etag / ETag header on remote url. Depending on secret content, HTTP connection is prepared
    * for handling proxy username/password, target service authentication (through basic and bearer authorization or
    * customer request header), remote SSL connection through installation of CA certificate or disabling SSL validation
    * (ie. accepting all certificate and hostname verifications).
    *
    * @param remoteUrl            The remote URL to check
    * @param secret               The secret associated with this remote URL (if any. Can be null)
    * @param disableSSLValidation Whether to disable SSL validation. If true, all SSL related information from secret
    *                             will be ignored.
    * @return The value of Etag / ETag header if any. null if none.
    * @throws IOException if anything goes wrong (request preparation or execution).
    */
   public static String getURLEtag(String remoteUrl, Secret secret, boolean disableSSLValidation) throws IOException {

      // Build remote URLConnection and the read response headers.
      HttpURLConnection connection = prepareURLConnection(remoteUrl, secret, disableSSLValidation);

      try {
         // Try simple syntax.
         String etag = connection.getHeaderField("Etag");
         if (etag != null) {
            log.debug("Found an Etag for {} : {}", remoteUrl, etag);
            return etag;
         }
         // Try other syntax.
         etag = connection.getHeaderField("ETag");
         if (etag != null) {
            log.debug("Found an Etag for {} : {}", remoteUrl, etag);
            return etag;
         }
      } catch (Exception e) {
         log.error("Caught an exception while retrieving Etag for {}", remoteUrl, e);
      }
      log.debug("No Etag found for {} !", remoteUrl);
      return null;
   }

   /**
    * Handle the HTTP/HTTPS download of remote url as a local temporary file. Depending on secret content, HTTP
    * connection is prepared for handling proxy username/password, target service authentication (through basic and
    * bearer authorization or customer request header), remote SSL connection through installation of CA certificate or
    * disabling SSL validation (ie. accepting all certificate and hostname verifications).
    *
    * @param remoteUrl            The remote URL to download and transfer into resulting file
    * @param secret               The secret associated with this remote URL (if any. Can be null)
    * @param disableSSLValidation Whether to disable SSL validation. If true, all SSL related information from secret
    *                             will be ignored.
    * @return A temporary file containing downloaded content.
    * @throws IOException if anything goes wrong (request preparation or execution).
    */
   public static File handleHTTPDownloadToFile(String remoteUrl, Secret secret, boolean disableSSLValidation)
         throws IOException {

      // Build remote URLConnection and local target file.
      HttpURLConnection connection = prepareURLConnection(remoteUrl, secret, disableSSLValidation);
      File localFile = File.createTempFile("microcks-" + System.currentTimeMillis(), ".download");

      try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
            // Transfer file to local.
            FileOutputStream fos = new FileOutputStream(localFile);) {
         fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
      }
      return localFile;
   }

   /**
    * Handle the HTTP/HTTPS download of remote url as a local temporary file. Depending on secret content, HTTP
    * connection is prepared for handling proxy username/password, target service authentication (through basic and
    * bearer authorization or customer request header), remote SSL connection through installation of CA certificate or
    * disabling SSL validation (ie. accepting all certificate and hostname verifications).
    *
    * @param remoteUrl            The remote URL to download and transfer into resulting file
    * @param secret               The secret associated with this remote URL (if any. Can be null)
    * @param disableSSLValidation Whether to disable SSL validation. If true, all SSL related information from secret
    *                             will be ignored.
    * @return A temporary file containing downloaded content as well as Http download headers.
    * @throws IOException if anything goes wrong (request preparation or execution).
    */
   public static FileAndHeaders handleHTTPDownloadToFileAndHeaders(String remoteUrl, Secret secret,
         boolean disableSSLValidation) throws IOException {

      // Build remote URLConnection and local target file.
      HttpURLConnection connection = prepareURLConnection(remoteUrl, secret, disableSSLValidation);
      File localFile = File.createTempFile("microcks-" + System.currentTimeMillis(), ".download");

      Map<String, List<String>> responseHeaders = null;
      try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
            // Transfer file to local.
            FileOutputStream fos = new FileOutputStream(localFile);) {
         fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
         responseHeaders = connection.getHeaderFields();
      }
      return new FileAndHeaders(localFile, responseHeaders);
   }

   /**
    * Prepare an URLConnection with all the security related stuffs specified by optional secret.
    */
   private static HttpURLConnection prepareURLConnection(String remoteUrl, Secret secret, boolean disableSSLValidation)
         throws IOException {

      // Build remote URL and connection to prepare.
      URL website = new URL(remoteUrl);

      HttpURLConnection connection = (HttpURLConnection) website.openConnection();

      // If HTTPS and SSL validation is disabled, trust everything.
      if ("https".equals(website.getProtocol())) {
         try {
            if (disableSSLValidation) {
               log.debug("SSL Validation is disabled for {}, installing accept everything TrustManager", remoteUrl);
               installAcceptEverythingTrustManager(connection);
            } else if (secret != null && secret.getCaCertPem() != null && secret.getCaCertPem().trim().length() > 0) {
               log.debug("Secret for {} contains a CA Cert, installing certificate into TrustManager", remoteUrl);
               installCustomCaCertTrustManager(secret.getCaCertPem(), connection);
            }
         } catch (Exception e) {
            log.error("Caught exception while preparing TrustManager for connecting {}: {}", remoteUrl, e.getMessage());
            throw new IOException("SSL Connection with " + remoteUrl + " failed during preparation", e);
         }
      }

      if (secret != null) {
         // If Basic authentication required, set request property.
         if (secret.getUsername() != null && secret.getPassword() != null) {
            log.debug("Secret for {} contains username/password, assuming Authorization Basic", remoteUrl);
            // Building a base64 string.
            String encoded = Base64.getEncoder()
                  .encodeToString((secret.getUsername() + ":" + secret.getPassword()).getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encoded);
         }

         // If Token authentication required, set request property.
         if (secret.getToken() != null) {
            if (secret.getTokenHeader() != null && secret.getTokenHeader().trim().length() > 0) {
               log.debug("Secret for {} contains token and token header, adding them as request header", remoteUrl);
               connection.setRequestProperty(secret.getTokenHeader().trim(), secret.getToken());
            } else {
               log.debug("Secret for {} contains token only, assuming Authorization Bearer", remoteUrl);
               connection.setRequestProperty("Authorization", "Bearer " + secret.getToken());
            }
         }
      }

      return connection;
   }

   /**
    * Install a TrustManager that accept every verification of host name.
    */
   private static void installAcceptEverythingTrustManager(HttpURLConnection connection) throws Exception {
      // Create a trust manager that does not validate certificate chains
      TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
         public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
         }

         public void checkClientTrusted(X509Certificate[] certs, String authType) {
            // No check to do here as we must accept everything.
         }

         public void checkServerTrusted(X509Certificate[] certs, String authType) {
            // No check to do here as we must accept everything.
         }
      } };

      // Install the all-trusting trust manager.
      final SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());

      // Create and install all-trusting host name verifier.
      HostnameVerifier allHostsValid = (hostname, session) -> true;
      ((HttpsURLConnection) connection).setHostnameVerifier(allHostsValid);
   }

   /**
    * Install a TrustManager that validates the CA certificate.
    */
   private static void installCustomCaCertTrustManager(String caCertPem, HttpURLConnection connection)
         throws Exception {
      // First compute a stripped PEM certificate and decode it from base64.
      String strippedPem = caCertPem.replace(BEGIN_CERTIFICATE, "").replace(END_CERTIFICATE, "");
      InputStream is = new ByteArrayInputStream(org.apache.commons.codec.binary.Base64.decodeBase64(strippedPem));

      // Generate a new x509 certificate from the stripped decoded pem.
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate caCert = (X509Certificate) cf.generateCertificate(is);

      // Set a new certificate into keystore.
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null); // You don't need the KeyStore instance to come from a file.
      ks.setCertificateEntry("caCert", caCert);

      tmf.init(ks);

      // Install the new TrustManager.
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tmf.getTrustManagers(), null);
      ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
   }


   /** Simple wrapper around a downloaded local file and the header we received during the download. */
   public static class FileAndHeaders {
      private File localFile;
      private Map<String, List<String>> responseHeaders;

      public FileAndHeaders(File localFile, Map<String, List<String>> responseHeaders) {
         this.localFile = localFile;
         this.responseHeaders = responseHeaders;
      }

      public File getLocalFile() {
         return localFile;
      }

      public Map<String, List<String>> getResponseHeaders() {
         return responseHeaders;
      }
   }
}
