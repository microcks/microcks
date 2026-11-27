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
package io.microcks.web.grpc.vertx;

import io.microcks.artifact.grpc.GrpcMetadataUtil;
import io.microcks.web.grpc.GrpcMockHandlerRegistry;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Drainable;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.protobuf.services.BinaryLogProvider;
import io.quarkus.runtime.ShutdownEvent;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServiceBridge;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serves the gRPC mocks (and the dynamic reflection service) on the main Quarkus HTTP port, with Vert.x gRPC. The
 * {@link GrpcMockHandlerRegistry} plays the role of the "fallback registry": any method name is resolved on each call,
 * so services imported after startup are immediately served. It replaces the former Netty based server (separate port).
 * @author laurent
 */
@ApplicationScoped
public class VertxGrpcMockServer {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(VertxGrpcMockServer.class);

   private static final String GRPC_CONTENT_TYPE = "application/grpc";

   private final Vertx vertx;
   private final GrpcMockHandlerRegistry registry;

   // Mocks read MongoDB synchronously: callbacks must not run on the event loop.
   private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

   @ConfigProperty(name = "grpc.vertx.enabled", defaultValue = "true")
   boolean enabled = true;

   @Inject
   public VertxGrpcMockServer(Vertx vertx, GrpcMockHandlerRegistry registry) {
      this.vertx = vertx;
      this.registry = registry;
   }

   /** Mount the gRPC route on the main router. It only takes HTTP/2 requests carrying a gRPC content type. */
   void onRouter(@Observes Router router) {
      if (!enabled) {
         return;
      }
      log.info("Serving gRPC mocks on the main HTTP port (Vert.x gRPC)");
      router.route().order(-100).handler(this::handle);
   }

   void onStop(@Observes ShutdownEvent event) {
      workers.shutdown();
   }

   private void handle(RoutingContext ctx) {
      HttpServerRequest request = ctx.request();
      String contentType = request.getHeader("content-type");
      if (request.version() != HttpVersion.HTTP_2 || contentType == null
            || !contentType.toLowerCase().startsWith(GRPC_CONTENT_TYPE)) {
         ctx.next();
         return;
      }
      String fullMethodName = request.path().startsWith("/") ? request.path().substring(1) : request.path();
      int slash = fullMethodName.indexOf('/');
      ServerMethodDefinition<?, ?> method = slash > 0 ? registry.lookupMethod(fullMethodName, null) : null;
      if (method == null) {
         ctx.next();
         return;
      }

      String remoteAddress = request.remoteAddress() != null ? request.remoteAddress().host() : null;
      ServerServiceDefinition definition = ServerInterceptors.intercept(
            ServerServiceDefinition.builder(fullMethodName.substring(0, slash)).addMethod(method).build(),
            new HeaderInterceptor(remoteAddress));
      ServerServiceDefinition.Builder offloaded = ServerServiceDefinition.builder(fullMethodName.substring(0, slash));
      definition.getMethods().forEach(m -> offloaded.addMethod(offload(m)));

      GrpcServer server = GrpcServer.server(vertx);
      GrpcServiceBridge.bridge(offloaded.build()).bind(server);
      server.handle(request);
   }

   private <Q, R> ServerMethodDefinition<Q, R> offload(ServerMethodDefinition<Q, R> method) {
      return ServerMethodDefinition.create(vertxCompatible(method.getMethodDescriptor()),
            new OffloadHandler<>(method.getServerCallHandler()));
   }

   /** The Vert.x bridge needs {@link Drainable} streams, which the raw byte[] marshaller of the mocks does not give. */
   @SuppressWarnings("unchecked")
   private static <Q, R> MethodDescriptor<Q, R> vertxCompatible(MethodDescriptor<Q, R> descriptor) {
      if (descriptor.getRequestMarshaller() == BinaryLogProvider.BYTEARRAY_MARSHALLER) {
         MethodDescriptor.Marshaller<Q> marshaller = (MethodDescriptor.Marshaller<Q>) DRAINABLE_BYTES;
         return descriptor.toBuilder(marshaller, (MethodDescriptor.Marshaller<R>) DRAINABLE_BYTES).build();
      }
      return descriptor;
   }

   private static final MethodDescriptor.Marshaller<byte[]> DRAINABLE_BYTES = new MethodDescriptor.Marshaller<>() {
      @Override
      public InputStream stream(byte[] value) {
         return new DrainableBytes(value);
      }

      @Override
      public byte[] parse(InputStream stream) {
         try {
            return stream.readAllBytes();
         } catch (IOException e) {
            throw new UncheckedIOException(e);
         }
      }
   };

   private static class DrainableBytes extends ByteArrayInputStream implements Drainable {

      DrainableBytes(byte[] bytes) {
         super(bytes);
      }

      @Override
      public int drainTo(OutputStream target) throws IOException {
         int length = available();
         target.write(buf, pos, length);
         pos = count;
         return length;
      }
   }

   /** Make the remote client address and the headers available to the mock handlers. */
   private static class HeaderInterceptor implements ServerInterceptor {

      private final String remoteAddress;

      HeaderInterceptor(String remoteAddress) {
         this.remoteAddress = remoteAddress;
      }

      @Override
      public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
            ServerCallHandler<Q, R> next) {
         if (remoteAddress != null) {
            headers.put(GrpcMetadataUtil.REMOTE_ADDR_METADATA_KEY, remoteAddress);
         }
         Context context = Context.current().withValue(GrpcMetadataUtil.METADATA_CTX_KEY, headers);
         return Contexts.interceptCall(context, call, headers, next);
      }
   }

   /** Run the listener callbacks of a call, in order, on a worker instead of the Vert.x event loop. */
   private class OffloadHandler<Q, R> implements ServerCallHandler<Q, R> {

      private final ServerCallHandler<Q, R> delegate;

      OffloadHandler(ServerCallHandler<Q, R> delegate) {
         this.delegate = delegate;
      }

      @Override
      public ServerCall.Listener<Q> startCall(ServerCall<Q, R> call, Metadata headers) {
         SerializingExecutor executor = new SerializingExecutor(workers);
         return new SimpleForwardingServerCallListener<>(delegate.startCall(call, headers)) {
            @Override
            public void onMessage(Q message) {
               executor.execute(() -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
               executor.execute(super::onHalfClose);
            }

            @Override
            public void onCancel() {
               executor.execute(super::onCancel);
            }

            @Override
            public void onComplete() {
               executor.execute(super::onComplete);
            }

            @Override
            public void onReady() {
               executor.execute(super::onReady);
            }
         };
      }
   }
}
