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
package io.microcks.config;

import io.grpc.ManagedChannelRegistry;
import io.grpc.netty.NettyChannelProvider;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;

/**
 * In the native image, the default gRPC provider registries come out empty because gRPC discovers its transports
 * through a {@code ServiceLoader} that native-image does not resolve for them. We register the Netty client transport
 * explicitly, as the gRPC test runner needs it. The gRPC mocks themselves are served by Vert.x.
 * @author laurent
 */
@ApplicationScoped
public class GrpcServiceLoaderSupport {

   /** Registers the gRPC Netty channel provider in the default registries. */
   void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event) {
      ManagedChannelRegistry.getDefaultRegistry().register(new NettyChannelProvider());
   }
}
