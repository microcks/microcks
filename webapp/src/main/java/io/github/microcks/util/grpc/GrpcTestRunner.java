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

import io.github.microcks.domain.Header;
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
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.util.test.AbstractTestRunner;
import io.github.microcks.util.test.TestRunnerCommons;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Deadline;
import io.grpc.ForwardingClientCall;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.ClientCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * An extension of AbstractTestRunner that deals with GRPC calls. Response is received as byte array and then parsed as
 * DynamicMessage to check that it conforms with Service Protobuf description.
 * @author laurent
 */
public class GrpcTestRunner extends AbstractTestRunner<HttpMethod> {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(GrpcTestRunner.class);

   /** Call Option used to pass gRPC Metadata from client invocation to header client interceptor */
   public static final String CUSTOM_CALL_OPTION_NAME = "request-metadata";
   /** Call Option key for passing Metadata. */
   public static final CallOptions.Key<Metadata> METADATA_CUSTOM_CALL_OPTION = CallOptions.Key
         .createWithDefault(CUSTOM_CALL_OPTION_NAME, null);

   private final ResourceRepository resourceRepository;
   private final ResponseRepository responseRepository;

   private final boolean validateResponseCode;

   private long timeout = 10000L;
   private Secret secret;


   /**
    * Build a new GrpcTestRunner.
    * @param resourceRepository   Access to resources repository
    * @param responseRepository   Access to response repository
    * @param validateResponseCode whether to validate response code
    */
   public GrpcTestRunner(ResourceRepository resourceRepository, ResponseRepository responseRepository,
         boolean validateResponseCode) {
      this.resourceRepository = resourceRepository;
      this.responseRepository = responseRepository;
      this.validateResponseCode = validateResponseCode;
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
      String fullMethodName = service.getName() + "/" + operation.getName();

      // Build a new GRPC Channel from endpoint URL.
      URL endpoint = new URI(endpointUrl).toURL();
      ManagedChannel originChannel = buildManagedChannel(endpoint);

      // Add a custom header interceptor which adds the request-specific headers to every operation.
      ClientInterceptor headerInterceptor = new HeaderInterceptor();
      Channel channel = ClientInterceptors.intercept(originChannel, headerInterceptor);

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
         log.error("Protobuf descriptor cannot be read or parsed: {}", e.getMessage());
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
         String contentResponse = null;
         Status status = Status.OK;

         reqBuilder.clear();
         resBuilder.clear();

         // Now produce the request message byte array.
         parser.merge(request.getContent(), reqBuilder);
         byte[] requestBytes = reqBuilder.build().toByteArray();

         // Build CallOptions with deadline from timeout and secret.
         CallOptions callOptions = CallOptions.DEFAULT.withDeadline(Deadline.after(timeout, TimeUnit.MILLISECONDS));
         callOptions = addSecretToCall(secret, callOptions);

         // Add all headers as customOptions to callOptions
         Set<Header> headers = TestRunnerCommons.collectHeaders(testResult, request, operation);
         callOptions = callOptions.withOption(METADATA_CUSTOM_CALL_OPTION, convertHeadersToMetadata(headers));

         // Actually execute request.
         long startTime = System.currentTimeMillis();

         // Prepare response bytes holder.
         byte[] responseBytes = null;
         try {
            responseBytes = ClientCalls.blockingUnaryCall(channel,
                  GrpcUtil.buildGenericUnaryMethodDescriptor(fullMethodName), callOptions, requestBytes);
         } catch (StatusRuntimeException sre) {
            log.debug("StatusRuntimeException while executing grpc request {} on {}", fullMethodName, endpointUrl, sre);
            status = sre.getStatus();
            contentResponse = status.getDescription();
            message = String.format("Request failed with '%s' and description '%s'", status.getCode().name(),
                  contentResponse);
         }
         long duration = System.currentTimeMillis() - startTime;

         // If required, compare response code to expected ones.
         if (validateResponseCode) {
            // If we have to validate response code, don't consider this as a failure.
            Response expectedResponse = responseRepository.findById(request.getResponseId()).orElse(null);
            if (expectedResponse != null) {
               log.debug("Response expected status code: {}", expectedResponse.getStatus());
               // Beware of people putting 200 as expected code instead of OK.
               if (!String.valueOf(status.getCode().value()).equals(expectedResponse.getStatus())
                     && (!status.isOk() && "200".equals(expectedResponse.getStatus()))) {
                  log.debug("Response Status does not match expected one, returning failure");
                  message = String.format("Response Status does not match expected one. Expecting %s but got %d",
                        expectedResponse.getStatus(), status.getCode().value());
                  code = TestReturn.FAILURE_CODE;
               }
            }
         }

         // If still in success and no content yet, validate and parse response.
         if (code == TestReturn.SUCCESS_CODE && responseBytes != null) {
            contentResponse = new String(responseBytes, StandardCharsets.UTF_8);

            try {
               // Validate incoming message parsing a DynamicMessage.
               DynamicMessage respMsg = DynamicMessage.parseFrom(md.getOutputType(), responseBytes);

               // Now update response content with readable content.
               contentResponse = printer.print(respMsg);
            } catch (InvalidProtocolBufferException ipbe) {
               log.error("Received bytes cannot be transformed in {}", md.getOutputType().getFullName());
               code = TestReturn.FAILURE_CODE;
               message = "Received bytes cannot be transformed in " + md.getOutputType().getFullName();
            }
         }

         // Create a Response object for returning.
         Response response = new Response();
         response.setStatus(status.getCode().name());
         response.setMediaType("application/x-protobuf");
         response.setContent(contentResponse);

         results.add(new TestReturn(code, duration, message, request, response));
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


   /** A gRPC ClientInterceptor that attaches headers to each request. */
   static class HeaderInterceptor implements ClientInterceptor {

      @Override
      @SuppressWarnings("java:S119") // Suppress warning about generic type names because we're following community conventions.
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {
         return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            /**
             * Extension of the AttachHeadersInterceptor by allowing custom headers on each request, passed through via
             * custom CallOption.
             */
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
               // Extract custom headers from CallOptions
               Metadata customHeaders = callOptions.getOption(METADATA_CUSTOM_CALL_OPTION);
               if (customHeaders != null) {
                  log.debug("Adding headers to client request: {}", customHeaders.keys());
                  headers.merge(customHeaders);
               }
               super.start(responseListener, headers);
            }
         };

      }

   }

   /** Create a managed channel for the endpoint. */
   private ManagedChannel buildManagedChannel(URL endpoint) throws IOException {
      if (endpoint.getProtocol().startsWith("https") || endpoint.getPort() == 443) {
         TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder();
         if (secret != null && secret.getCaCertPem() != null) {
            // Install a trust manager with custom CA certificate.
            tlsBuilder.trustManager(new ByteArrayInputStream(secret.getCaCertPem().getBytes(StandardCharsets.UTF_8)));
         } else {
            // Install a trust manager that accepts everything and does not validate certificate chains.
            tlsBuilder.trustManager(new X509TrustManager() {
               public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                  return null;
               }

               public void checkClientTrusted(X509Certificate[] certs, String authType) {
                  // Accept everything.
               }

               public void checkServerTrusted(X509Certificate[] certs, String authType) {
                  // Accept everything.
               }
            });
         }
         // Build a Channel using the TLS Builder.
         return Grpc.newChannelBuilderForAddress(endpoint.getHost(), endpoint.getPort(), tlsBuilder.build()).build();
      }

      // Build a simple Channel using no creds (now default to plain text so usePlainText() is no longer necessary).
      return Grpc
            .newChannelBuilderForAddress(endpoint.getHost(), endpoint.getPort(), InsecureChannelCredentials.create())
            .build();
   }

   /** Add secret credentials to call options if any. */
   private CallOptions addSecretToCall(Secret secret, CallOptions callOptions) {
      if (secret != null && secret.getToken() != null) {
         log.debug("Secret contains token and maybe token header, adding them as call credentials");
         return callOptions.withCallCredentials(new TokenCallCredentials(secret.getToken(), secret.getTokenHeader()));
      }
      return callOptions;
   }

   /** Convert a set of Headers to gRPC Metadata. */
   private static Metadata convertHeadersToMetadata(Set<Header> headers) {
      Metadata metadata = new Metadata();
      for (Header header : headers) {
         for (String value : header.getValues()) {
            metadata.put(Metadata.Key.of(header.getName(), Metadata.ASCII_STRING_MARSHALLER), value);
         }
      }
      return metadata;
   }
}
