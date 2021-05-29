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
package io.github.microcks.util.grpc;

import io.github.microcks.web.GrpcServerCallHandler;

import io.grpc.HandlerRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

/**
 * A GRPC HandlerRegistry that delegates server calls handling to {@class GrpcServerCallHandler}.
 * @author laurent
 */
@Component
public class GrpcMockHandlerRegistry extends HandlerRegistry {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GrpcMockHandlerRegistry.class);

   @Autowired
   private GrpcServerCallHandler serverCallHandler;

   @Nullable
   @Override
   public ServerMethodDefinition<?, ?> lookupMethod(String fullMethodName, @Nullable String authority) {
      log.debug("lookupMethod() with fullMethodName: " + fullMethodName);
      return ServerMethodDefinition.create(mockMethodDescriptor(fullMethodName), mockServerCallHandler(fullMethodName));
   }


   protected MethodDescriptor<byte[], byte[]> mockMethodDescriptor(String fullMethodName) {
      return GrpcUtil.buildGenericUnaryMethodDescriptor(fullMethodName);
   }

   protected ServerCallHandler<byte[], byte[]> mockServerCallHandler(String fullMethodName) {
      return serverCallHandler.getUnaryServerCallHandler(fullMethodName);
   }
}
