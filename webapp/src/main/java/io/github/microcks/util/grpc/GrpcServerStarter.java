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

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.TlsServerCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * This is starter component for building, starting and managing shutdown of a GRPC server handking mock calls.
 * @author laurent
 */
@Component
public class GrpcServerStarter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GrpcServerStarter.class);

   @Value("${grpc.server.port:9090}")
   private final Integer serverPort = 9090;

   @Value("${grpc.server.certChainFilePath:}")
   private final String certChainFilePath = null;

   @Value("${grpc.server.privateKeyFilePath:}")
   private final String privateKeyFilePath = null;

   @Autowired
   private GrpcMockHandlerRegistry mockHandlerRegistry;

   private AtomicBoolean isRunning = new AtomicBoolean(false);

   private CountDownLatch latch;

   @PostConstruct
   public void startGrpcServer() {
      try {
         latch = new CountDownLatch(1);

         Server grpcServer = null;
         // If cert and private key is provided, build a TLS capable server.
         if (certChainFilePath != null && certChainFilePath.length() > 0
               && privateKeyFilePath != null && privateKeyFilePath.length() > 0) {
            TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                  .keyManager(new File(certChainFilePath), new File(privateKeyFilePath));
            grpcServer = Grpc.newServerBuilderForPort(serverPort, tlsBuilder.build())
                  .fallbackHandlerRegistry(mockHandlerRegistry)
                  .build();
         } else {
            // Else build a "plain text" server.
            grpcServer = ServerBuilder.forPort(serverPort)
                  .fallbackHandlerRegistry(mockHandlerRegistry)
                  .build();
         }
         grpcServer.start();
         log.info("GRPC Server started on port " + serverPort);

         Server finalGrpcServer = grpcServer;
         Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
               try {
                  if (finalGrpcServer != null) {
                     log.info("Shutting down gRPC server since JVM is shutting down");
                     finalGrpcServer.shutdown().awaitTermination(2, TimeUnit.SECONDS);
                  }
               } catch (InterruptedException e) {
                  e.printStackTrace();
               }
            }
         });
         startDaemonAwaitThread();
      } catch (Exception e) {
         log.error("GRPC Server cannot be started", e);
      }
   }

   private void startDaemonAwaitThread() {
      Thread awaitThread = new Thread(() -> {
         try {
            isRunning.set(true);
            latch.await();
         } catch (InterruptedException e) {
            log.error("GRPC Server awaiter interrupted.", e);
         }finally {
            isRunning.set(false);
         }
      });
      awaitThread.setName("grpc-server-awaiter");
      awaitThread.setDaemon(false);
      awaitThread.start();
   }
}
