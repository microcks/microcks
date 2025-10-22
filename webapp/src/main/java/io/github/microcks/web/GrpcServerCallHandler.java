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
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;

import io.github.microcks.service.OpenTelemetryResolverService;
import io.github.microcks.util.grpc.GrpcMetadataUtil;
import io.github.microcks.util.grpc.GrpcUtil;
import io.github.microcks.util.tracing.CommonAttributes;
import io.github.microcks.util.tracing.CommonEvents;
import io.github.microcks.util.tracing.TraceUtil;

import io.grpc.Metadata;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A Handler for GRPC Server calls invocation that is using Microcks dispatching and mock definitions.
 * @author laurent
 */
@Component
public class GrpcServerCallHandler {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(GrpcServerCallHandler.class);

   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;

   private final GrpcInvocationProcessor invocationProcessor;
   private final OpenTelemetryResolverService opentelemetryResolverService;

   /**
    * Build a new GrpcServerCallHandler with all the repositories it needs and application context.
    * @param serviceRepository            Repository for getting service definitions
    * @param resourceRepository           Repository for getting service resources definitions
    * @param invocationProcessor          The invocation processor to apply gRPC mocks dispatching logic
    * @param opentelemetryResolverService The opentelemetry resolver
    */
   public GrpcServerCallHandler(ServiceRepository serviceRepository, ResourceRepository resourceRepository,
         GrpcInvocationProcessor invocationProcessor, OpenTelemetryResolverService opentelemetryResolverService) {
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
      this.invocationProcessor = invocationProcessor;
      this.opentelemetryResolverService = opentelemetryResolverService;
   }

   /**
    * Create an ServerCallHandler that uses Microcks mocks for unary calls.
    * @param fullMethodName The GRPC method full name.
    * @return A ServerCallHandler
    */
   public ServerCallHandler<byte[], byte[]> getUnaryServerCallHandler(String fullMethodName) {
      return ServerCalls.asyncUnaryCall(new MockedUnaryMethod(fullMethodName));
   }

   /**
    * This internal class is handling UnaryMethod calls. It takes care of building a JSON representation from input,
    * apply a dispatcher to find correct response and serialize response JSON content into binary back.
    */
   protected class MockedUnaryMethod implements ServerCalls.UnaryMethod<byte[], byte[]> {

      private final String fullMethodName;
      private final String serviceName;
      private final String serviceVersion;
      private final String operationName;

      /**
       * Build a UnaryMethod for handling GRPC call.
       * @param fullMethodName The GRPC method full identifier.
       */
      public MockedUnaryMethod(String fullMethodName) {
         this.fullMethodName = fullMethodName;
         // Retrieve operation name, service name and version from fullMethodName.
         operationName = fullMethodName.substring(fullMethodName.indexOf("/") + 1);
         serviceName = fullMethodName.substring(0, fullMethodName.indexOf("/"));
         String packageName = fullMethodName.substring(0, fullMethodName.lastIndexOf("."));
         String[] parts = packageName.split("\\.");
         serviceVersion = (parts.length > 2 ? parts[parts.length - 1] : packageName);
      }

      @Override
      public void invoke(byte[] bytes, StreamObserver<byte[]> streamObserver) {
         log.info("Servicing mock response for service [{}, {}] and method {}", serviceName, serviceVersion,
               operationName);

         // Create an SERVER child span explicitly because Spring AOP does not apply to current method.
         Tracer tracer = opentelemetryResolverService.getOpenTelemetry()
               .getTracer(GrpcServerCallHandler.class.getName());
         Span span = tracer.spanBuilder("invokeMockedUnaryMethod").setSpanKind(SpanKind.SERVER).startSpan();

         try (Scope ignored = span.makeCurrent()) {
            // Delegate processing to doInvoke().
            doInvoke(bytes, streamObserver);
         } finally {
            span.end();
         }
      }

      private void doInvoke(byte[] bytes, StreamObserver<byte[]> streamObserver) {
         long startTime = System.currentTimeMillis();

         // Mark current span as explain-trace and set attributes.
         TraceUtil.enableExplainTracing();
         Span.current().setAttribute(CommonAttributes.SERVICE_NAME, serviceName);
         Span.current().setAttribute(CommonAttributes.SERVICE_VERSION, serviceVersion);
         Span.current().setAttribute(CommonAttributes.OPERATION_NAME, operationName);

         // Get metadata for current context to find remote client address put there by HeaderInterceptor.
         Metadata metadata = GrpcMetadataUtil.METADATA_CTX_KEY.get();
         String remoteAddr = metadata.get(GrpcMetadataUtil.REMOTE_ADDR_METADATA_KEY);

         // Add an event for the invocation reception with a human-friendly message.
         Span.current().addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(), TraceUtil
               .explainSpanEventBuilder(
                     String.format("Received gRPC invocation %s-%s on %s", serviceName, serviceVersion, operationName))
               .put(CommonAttributes.BODY_SIZE, bytes.length)
               .put(CommonAttributes.BODY_CONTENT,
                     (bytes.length > 1000 ? new String(bytes, StandardCharsets.UTF_8).substring(0, 1000) + "..."
                           : new String(bytes, StandardCharsets.UTF_8)))
               .put(CommonAttributes.CLIENT_ADDRESS, remoteAddr).build());

         try {
            // Get service and spotted operation.
            Service service = serviceRepository.findByNameAndVersion(serviceName, serviceVersion);
            if (service == null) {
               // No service found.
               log.debug("No GRPC Service def found for [{}, {}]", serviceName, serviceVersion);
               streamObserver.onError(Status.UNIMPLEMENTED
                     .withDescription("No GRPC Service def found for " + fullMethodName).asException());
               return;
            }
            Operation grpcOperation = null;
            for (Operation operation : service.getOperations()) {
               if (operation.getName().equals(operationName)) {
                  grpcOperation = operation;
                  break;
               }
            }

            if (grpcOperation != null) {
               log.debug("Found a valid operation {} with rules: {}", grpcOperation.getName(),
                     grpcOperation.getDispatcherRules());

               // In order to inspect incoming byte array, we need the Protobuf binary descriptor that should
               // have been processed while importing the .proto schema for the service.
               List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(),
                     ResourceType.PROTOBUF_DESCRIPTOR);
               if (resources == null || resources.size() != 1) {
                  log.error("Did not found any pre-processed Protobuf binary descriptor...");
                  streamObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription("No pre-processed Protobuf binary descriptor found").asException());
                  return;
               }
               Resource pbResource = resources.getFirst();

               // Get the method descriptor and type registry.
               Descriptors.MethodDescriptor md = GrpcUtil.findMethodDescriptor(pbResource.getContent(), serviceName,
                     operationName);
               TypeRegistry registry = GrpcUtil.buildTypeRegistry(pbResource.getContent());

               // Now parse the incoming message.
               DynamicMessage inMsg = DynamicMessage.parseFrom(md.getInputType(), bytes);
               String jsonBody = JsonFormat.printer().usingTypeRegistry(registry).print(inMsg);
               log.debug("Request body: {}", jsonBody);

               Span.current()
                     .addEvent(CommonEvents.BODY_PARSED.getEventName(),
                           TraceUtil.explainSpanEventBuilder("GRPC input bytes have been converted in JSON string")
                                 .put(CommonAttributes.BODY_SIZE, jsonBody.length())
                                 .put(CommonAttributes.BODY_CONTENT,
                                       (jsonBody.length() > 1000 ? jsonBody.substring(0, 1000) + "..." : jsonBody))
                                 .build());

               MockInvocationContext ic = new MockInvocationContext(service, grpcOperation, null);
               GrpcResponseResult response = invocationProcessor.processInvocation(ic, startTime, jsonBody);

               if (!response.isError()) {
                  // Use a builder for out type with a Json parser to merge content and build outMsg.
                  DynamicMessage.Builder outBuilder = DynamicMessage.newBuilder(md.getOutputType());

                  JsonFormat.parser().usingTypeRegistry(registry).merge(response.content(), outBuilder);
                  DynamicMessage outMsg = outBuilder.build();

                  // Send the output message and complete the stream.
                  streamObserver.onNext(outMsg.toByteArray());
                  streamObserver.onCompleted();

               } else {
                  // Error during invocation processing. Write it to the stream.
                  streamObserver.onError(response.status().withDescription(response.errorDescription()).asException());
               }

            } else {
               // No operation found.
               log.debug("No valid operation found for [{}, {}] and {}", serviceName, serviceVersion, operationName);
               streamObserver.onError(Status.UNIMPLEMENTED
                     .withDescription("No valid operation found for " + fullMethodName).asException());
            }
         } catch (Exception t) {
            log.error("Unexpected throwable during GRPC input request processing", t);
            streamObserver
                  .onError(Status.UNKNOWN.withDescription("Unexpected throwable during GRPC input request processing")
                        .withCause(t).asException());
         }
      }
   }
}
