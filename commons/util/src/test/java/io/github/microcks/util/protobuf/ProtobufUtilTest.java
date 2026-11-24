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
package io.github.microcks.util.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for ProtobufUtil class.
 * @author pbvittal
 */
public class ProtobufUtilTest {

   /** Compiled descriptor set of samples/hello-v1.proto. */
   public static final String HELLO_DESCRIPTOR = "CrICCg5oZWxsby12MS5wcm90bxIgaW8uZ2l0aHViLm1pY3JvY2tzLmdycGMuaGVsbG8udjEiSAoMSGVsbG9SZXF1ZXN0EhwKCWZpcnN0bmFtZRgBIAEoCVIJZmlyc3RuYW1lEhoKCGxhc3RuYW1lGAIgASgJUghsYXN0bmFtZSIrCg1IZWxsb1Jlc3BvbnNlEhoKCGdyZWV0aW5nGAEgASgJUghncmVldGluZzJ7CgxIZWxsb1NlcnZpY2USawoIZ3JlZXRpbmcSLi5pby5naXRodWIubWljcm9ja3MuZ3JwYy5oZWxsby52MS5IZWxsb1JlcXVlc3QaLy5pby5naXRodWIubWljcm9ja3MuZ3JwYy5oZWxsby52MS5IZWxsb1Jlc3BvbnNlQgJQAWIGcHJvdG8z";

   @Test
   void testFindMessageDescriptor() throws Exception {
      assertEquals("io.github.microcks.grpc.hello.v1.HelloResponse", ProtobufUtil
            .findMessageDescriptor(HELLO_DESCRIPTOR, "io.github.microcks.grpc.hello.v1.HelloResponse").getFullName());
      assertEquals("io.github.microcks.grpc.hello.v1.HelloResponse",
            ProtobufUtil.findMessageDescriptor(HELLO_DESCRIPTOR, "HelloResponse").getFullName());
      // First message of the main file is the default.
      assertEquals("io.github.microcks.grpc.hello.v1.HelloRequest",
            ProtobufUtil.findMessageDescriptor(HELLO_DESCRIPTOR, null).getFullName());
      assertThrows(IllegalArgumentException.class,
            () -> ProtobufUtil.findMessageDescriptor(HELLO_DESCRIPTOR, "UnknownMessage"));
   }

   @Test
   void testFindNestedMessageDescriptor() throws Exception {
      DescriptorProtos.DescriptorProto inner = DescriptorProtos.DescriptorProto.newBuilder().setName("Inner").build();
      DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.newBuilder()
            .addFile(DescriptorProtos.FileDescriptorProto.newBuilder().setName("outer.proto").setPackage("acme")
                  .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Outer").addNestedType(inner)))
            .build();
      String base64Descriptor = Base64.getEncoder().encodeToString(fds.toByteArray());

      assertEquals("acme.Outer.Inner",
            ProtobufUtil.findMessageDescriptor(base64Descriptor, "acme.Outer.Inner").getFullName());
      assertEquals("acme.Outer.Inner", ProtobufUtil.findMessageDescriptor(base64Descriptor, "Inner").getFullName());
   }

   @Test
   void testJsonToProtobufRoundTrip() throws Exception {
      Descriptors.Descriptor descriptor = ProtobufUtil.findMessageDescriptor(HELLO_DESCRIPTOR, "HelloRequest");

      byte[] bytes = ProtobufUtil.jsonToProtobuf("{\"firstname\": \"Laurent\", \"lastname\": \"Broudoux\"}",
            descriptor);
      // Field 1 (length-delimited) "Laurent" then field 2 "Broudoux".
      assertEquals("0a074c617572656e74120842726f75646f7578", HexFormat.of().formatHex(bytes));
      assertEquals("{\"firstname\":\"Laurent\",\"lastname\":\"Broudoux\"}",
            ProtobufUtil.protobufToJson(bytes, descriptor).replaceAll("\\s", ""));

      assertThrows(InvalidProtocolBufferException.class,
            () -> ProtobufUtil.jsonToProtobuf("{\"unknown\": \"field\"}", descriptor));
   }

   @Test
   void testGetValidationErrors() throws Exception {
      Descriptors.Descriptor descriptor = ProtobufUtil.findMessageDescriptor(HELLO_DESCRIPTOR, "HelloRequest");

      assertTrue(ProtobufUtil.getValidationErrors(HexFormat.of().parseHex("0a074c617572656e74"), descriptor).isEmpty());

      // Field number 3 is not declared in HelloRequest.
      List<String> errors = ProtobufUtil.getValidationErrors(HexFormat.of().parseHex("1a0568656c6c6f"), descriptor);
      assertEquals(1, errors.size());
      assertTrue(errors.getFirst().contains("Field number 3"));

      // JSON text is not a valid Protobuf payload.
      errors = ProtobufUtil.getValidationErrors("{\"firstname\":\"Laurent\"}".getBytes(), descriptor);
      assertEquals(1, errors.size());
      assertTrue(errors.getFirst().contains("cannot be parsed"));
   }
}
