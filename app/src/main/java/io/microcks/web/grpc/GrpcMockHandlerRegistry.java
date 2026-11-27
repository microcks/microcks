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

import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.web.GrpcServerCallHandler;

import io.grpc.HandlerRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.ServerCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

/**
 * A GRPC HandlerRegistry that delegates server calls handling to GrpcServerCallHandler.
 * @author laurent
 */
@Component
public class GrpcMockHandlerRegistry extends HandlerRegistry {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(GrpcMockHandlerRegistry.class);

   private static final String SERVER_REFLECTION_V1_METHOD = "grpc.reflection.v1.ServerReflection/ServerReflectionInfo";

   private final GrpcServerCallHandler serverCallHandler;

   private final ProtoReflectionService reflectionService;

   /**
    * Build a new GrpcMockHandlerRegistry with a callback handler.
    * @param serverCallHandler  The server callback handler to use
    * @param serviceRepository  The repository used to get access to service definitions
    * @param resourceRepository The repository used to get access to resource definitions
    */
   public GrpcMockHandlerRegistry(GrpcServerCallHandler serverCallHandler, ServiceRepository serviceRepository,
         ResourceRepository resourceRepository) {
      this.serverCallHandler = serverCallHandler;
      this.reflectionService = new ProtoReflectionService(serviceRepository, resourceRepository);
   }

   public ProtoReflectionService getReflectionService() {
      return reflectionService;
   }

   @Nullable
   @Override
   public ServerMethodDefinition<?, ?> lookupMethod(String fullMethodName, @Nullable String authority) {
      log.debug("lookupMethod() with fullMethodName: {}", fullMethodName);
      if (SERVER_REFLECTION_V1_METHOD.equals(fullMethodName)) {
         return ServerMethodDefinition.create(reflectionMethodDescriptor(), reflectionServerCallHandler());
      }
      return ServerMethodDefinition.create(mockMethodDescriptor(fullMethodName), mockServerCallHandler(fullMethodName));
   }

   protected MethodDescriptor<ServerReflectionRequest, ServerReflectionResponse> reflectionMethodDescriptor() {
      return MethodDescriptor
            .newBuilder(ProtoUtils.marshaller(ServerReflectionRequest.getDefaultInstance()),
                  ProtoUtils.marshaller(ServerReflectionResponse.getDefaultInstance()))
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING).setFullMethodName(SERVER_REFLECTION_V1_METHOD)
            .setSampledToLocalTracing(true).build();
   }

   protected ServerCallHandler<ServerReflectionRequest, ServerReflectionResponse> reflectionServerCallHandler() {
      return ServerCalls.asyncBidiStreamingCall(this.reflectionService::serverReflectionInfo);
   }

   protected MethodDescriptor<byte[], byte[]> mockMethodDescriptor(String fullMethodName) {
      return GrpcUtil.buildGenericUnaryMethodDescriptor(fullMethodName);
   }

   protected ServerCallHandler<byte[], byte[]> mockServerCallHandler(String fullMethodName) {
      return serverCallHandler.getUnaryServerCallHandler(fullMethodName);
   }
}
