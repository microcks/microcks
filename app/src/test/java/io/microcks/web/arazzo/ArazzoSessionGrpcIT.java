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
package io.microcks.web.arazzo;

import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.repository.arazzo.ArazzoSessionCallRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.web.AbstractBaseIT;

import io.grpc.CallOptions;
import io.grpc.ClientInterceptors;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A gRPC client names its Arazzo verification session in the x-microcks-session metadata, or in the microcks-session
 * entry of its baggage metadata: the gRPC mocks record its calls in that session.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSessionGrpcIT extends AbstractBaseIT {

   private static final String GREETING = "io.microcks.grpc.hello.v1.HelloService/greeting";

   @Inject
   private ArazzoSessionRepository arazzoSessionRepository;

   @Inject
   private ArazzoSessionCallRepository arazzoSessionCallRepository;

   @AfterEach
   void deleteSessions() {
      arazzoSessionRepository.deleteAll();
      arazzoSessionCallRepository.deleteAll();
   }

   @Test
   void aGrpcCallNamingASessionInItsMetadataIsRecordedInIt() throws Exception {
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/HelloService.postman.json", false);
      String first = openSession();
      String second = openSession();

      greet("x-microcks-session", first);
      greet("baggage", "tenant=bakery,microcks-session=" + second);

      for (String sessionId : List.of(first, second)) {
         List<ArazzoSessionCall> calls = arazzoSessionCallRepository.findBySessionIdOrderByIndexAsc(sessionId);
         assertEquals(List.of("0 GRPC greeting 0"), calls.stream().map(call -> call.getIndex() + " " + call.getMethod()
               + " " + call.getOperationName() + " " + call.getResponseStatus()).toList());
      }
   }

   private String openSession() {
      ArazzoSession session = new ArazzoSession();
      session.setWorkflowId("greetWorkflow");
      session.setOpenedDate(new Date());
      session.setExpireAt(new Date(System.currentTimeMillis() + 60_000L));
      return arazzoSessionRepository.save(session).getId();
   }

   /** Laurent Broudoux asks for a greeting, as protobuf bytes, carrying a metadata entry. */
   private void greet(String key, String value) throws InterruptedException {
      Metadata metadata = new Metadata();
      metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
      ManagedChannel channel = Grpc
            .newChannelBuilderForAddress("localhost", getServerPort(), InsecureChannelCredentials.create()).build();
      try {
         MethodDescriptor<byte[], byte[]> greeting = MethodDescriptor.<byte[], byte[]>newBuilder()
               .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName(GREETING)
               .setRequestMarshaller(new BytesMarshaller()).setResponseMarshaller(new BytesMarshaller()).build();
         ClientCalls.blockingUnaryCall(
               ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata)), greeting,
               CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), helloRequest("Laurent", "Broudoux"));
      } finally {
         channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      }
   }

   /** The HelloRequest message: its firstname and lastname, string fields 1 and 2. */
   private static byte[] helloRequest(String firstname, String lastname) {
      ByteArrayOutputStream message = new ByteArrayOutputStream();
      for (int field = 1; field <= 2; field++) {
         byte[] value = (field == 1 ? firstname : lastname).getBytes(StandardCharsets.UTF_8);
         message.write((field << 3) | 2);
         message.write(value.length);
         message.writeBytes(value);
      }
      return message.toByteArray();
   }

   private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
      @Override
      public InputStream stream(byte[] value) {
         return new ByteArrayInputStream(value);
      }

      @Override
      public byte[] parse(InputStream stream) {
         try {
            return stream.readAllBytes();
         } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
         }
      }
   }
}
