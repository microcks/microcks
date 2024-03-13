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
package io.github.microcks.util.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Secret;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.util.test.AbstractTestRunner;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.Deadline;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.ClientCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An extension of AbstractTestRunner that deals with GRPC calls. Response is received as byte array and then parsed as
 * DynamicMessage to chack that is conforms with Service Protobuf description.
 * @author laurent
 */
public class GrpcTestRunner extends AbstractTestRunner<HttpMethod> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GrpcTestRunner.class);

   private long timeout = 10000L;

   private Secret secret;

   private ResourceRepository resourceRepository;

   /**
    * Build a new GrpcTestRunner.
    * @param resourceRepository Access to resources repository
    */
   public GrpcTestRunner(ResourceRepository resourceRepository) {
      this.resourceRepository = resourceRepository;
   }

   /**
    * Set the timeout to apply for each request tests.
    * @param timeout Timeout value in milliseconds.
    */
   public void setTimeout(long timeout) {
      this.timeout = timeout;
   }

   /**
    * Set the Secret used for securing the requests.
    * @param secret The Secret used or securing the requests.
    */
   public void setSecret(Secret secret) {
      this.secret = secret;
   }

   @Override
   public List<TestReturn> runTest(Service service, Operation operation, TestResult testResult, List<Request> requests,
         String endpointUrl, HttpMethod method) throws URISyntaxException, IOException {

      log.debug("Launching test run on {} for {} request(s)", endpointUrl, requests.size());

      if (requests.isEmpty()) {
         return null;
      }

      // Initialize results.
      List<TestReturn> results = new ArrayList<>();

      // Rebuild the GRPC fullMethodName.
      String fullMethodName = service.getXmlNS() + "." + service.getName() + "/" + operation.getName();

      // Build a new GRPC Channel from endpoint URL.
      URL endpoint = new URL(endpointUrl);

      ManagedChannel channel;
      if (endpointUrl.startsWith("https://") || endpoint.getPort() == 443) {
         TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder();
         if (secret != null && secret.getCaCertPem() != null) {
            // Install a trust manager with custom CA certificate.
            tlsBuilder.trustManager(new ByteArrayInputStream(secret.getCaCertPem().getBytes(StandardCharsets.UTF_8)));
         } else {
            // Install a trust manager that accepts everything and does not validate certificate chains.
            tlsBuilder.trustManager(new TrustManager[] { new X509TrustManager() {
               public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                  return null;
               }

               public void checkClientTrusted(X509Certificate[] certs, String authType) {
               }

               public void checkServerTrusted(X509Certificate[] certs, String authType) {
               }
            } });
         }
         // Build a Channel using the TLS Builder.
         channel = Grpc.newChannelBuilderForAddress(endpoint.getHost(), endpoint.getPort(), tlsBuilder.build()).build();
      } else {
         // Build a simple Channel using plain text.
         channel = Grpc.newChannelBuilderForAddress(endpoint.getHost(), endpoint.getPort(), null).usePlaintext()
               .build();
      }

      // In order to produce outgoing byte array, we need the Protobuf binary descriptor that should
      // have been processed while importing the .proto schema for the service.
      List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(),
            ResourceType.PROTOBUF_DESCRIPTOR);
      if (resources == null || resources.size() != 1) {
         log.error("Could not found any pre-processed Protobuf binary descriptor...");
         results.add(new TestReturn(TestReturn.FAILURE_CODE, 0,
               "Could not found any pre-processed Protobuf binary descriptor...", null, null));
         return results;
      }
      Resource pbResource = resources.get(0);

      Descriptors.MethodDescriptor md = null;
      try {
         md = GrpcUtil.findMethodDescriptor(pbResource.getContent(), service.getName(), operation.getName());
      } catch (Exception e) {
         log.error("Protobuf descriptor cannot be read or parsed: " + e.getMessage());
         results.add(new TestReturn(TestReturn.FAILURE_CODE, 0,
               "Protobuf descriptor cannot be read or parsed: " + e.getMessage(), null, null));
         return results;
      }

      // Use a builder for out type with a Json parser to merge content and build outMsg.
      DynamicMessage.Builder reqBuilder = DynamicMessage.newBuilder(md.getInputType());
      DynamicMessage.Builder resBuilder = DynamicMessage.newBuilder(md.getOutputType());
      JsonFormat.Parser parser = JsonFormat.parser();
      JsonFormat.Printer printer = JsonFormat.printer();

      for (Request request : requests) {
         // Reset status code, message and request each time.
         int code = TestReturn.SUCCESS_CODE;
         String message = null;
         reqBuilder.clear();
         resBuilder.clear();

         // Now produce the request message byte array.
         parser.merge(request.getContent(), reqBuilder);
         byte[] requestBytes = reqBuilder.build().toByteArray();

         CallOptions callOptions = CallOptions.DEFAULT.withDeadline(Deadline.after(timeout, TimeUnit.MILLISECONDS));

         if (secret != null && secret.getToken() != null) {
            log.debug("Secret contains token and maybe token header, adding them as call credentials");
            callOptions.withCallCredentials(new TokenCallCredentials(secret.getToken(), secret.getTokenHeader()));
         }

         // Actually execute request.
         long startTime = System.currentTimeMillis();
         byte[] responseBytes = ClientCalls.blockingUnaryCall(channel,
               GrpcUtil.buildGenericUnaryMethodDescriptor(fullMethodName), callOptions, requestBytes);
         long duration = System.currentTimeMillis() - startTime;

         // Create a Response object for returning.
         Response response = new Response();
         response.setStatus("200");
         response.setMediaType("application/x-protobuf");
         response.setContent(new String(responseBytes, "UTF-8"));

         try {
            // Validate incoming message parsing a DynamicMessage.
            DynamicMessage respMsg = DynamicMessage.parseFrom(md.getOutputType(), responseBytes);

            // Now update response content with readable content.
            String respJson = printer.print(respMsg);
            response.setContent(respJson);

            results.add(new TestReturn(code, duration, message, request, response));
         } catch (InvalidProtocolBufferException ipbe) {
            log.error("Received bytes cannot be transformed in " + md.getOutputType().getFullName());
            results.add(new TestReturn(TestReturn.FAILURE_CODE, duration,
                  "Received bytes cannot be transformed in \" + md.getOutputType().getFullName()", request, response));
         }
      }

      return results;
   }

   /**
    * Build the HttpMethod corresponding to string.
    */
   @Override
   public HttpMethod buildMethod(String method) {
      return HttpMethod.POST;
   }
}
