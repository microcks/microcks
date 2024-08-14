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

import com.google.protobuf.Descriptors;
import com.google.protobuf.TypeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for GrpcUtil class.
 * @author laurent
 */
class GrpcUtilTest {

   @Test
   void testFindMethodDescriptor() {
      // This is the simple HelloService with no dependencies.
      String base64ProtobufDescriptor = "CrICCg5oZWxsby12MS5wcm90bxIgaW8uZ2l0aHViLm1pY3JvY2tzLmdycGMuaGVsbG8udjEiSAoMSGVsbG9SZXF1ZXN0EhwKCWZpcnN0bmFtZRgBIAEoCVIJZmlyc3RuYW1lEhoKCGxhc3RuYW1lGAIgASgJUghsYXN0bmFtZSIrCg1IZWxsb1Jlc3BvbnNlEhoKCGdyZWV0aW5nGAEgASgJUghncmVldGluZzJ7CgxIZWxsb1NlcnZpY2USawoIZ3JlZXRpbmcSLi5pby5naXRodWIubWljcm9ja3MuZ3JwYy5oZWxsby52MS5IZWxsb1JlcXVlc3QaLy5pby5naXRodWIubWljcm9ja3MuZ3JwYy5oZWxsby52MS5IZWxsb1Jlc3BvbnNlQgJQAWIGcHJvdG8z";

      Descriptors.MethodDescriptor desc = null;
      try {
         desc = GrpcUtil.findMethodDescriptor(base64ProtobufDescriptor, "HelloService", "greeting");
      } catch (Exception e) {
         fail("No exception should be thrown while parsing protobuf descriptor and searching service");
      }

      assertNotNull(desc);
      assertEquals("io.github.microcks.grpc.hello.v1.HelloService.greeting", desc.getFullName());
   }

   @Test
   void testFindMethodDescriptorWithDependency() {
      // This is the GoodbyeService with descriptor embedding the shared/uuid.proto dependency.
      String base64ProtobufDescriptor = "CjsKEXNoYXJlZC91dWlkLnByb3RvEgZzaGFyZWQiFgoEVVVJRBIOCgJpZBgBIAEoCVICaWRiBnByb3RvMwqDAwoQZ29vZGJ5ZS12MS5wcm90bxIiaW8uZ2l0aHViLm1pY3JvY2tzLmdycGMuZ29vZGJ5ZS52MRoRc2hhcmVkL3V1aWQucHJvdG8iSgoOR29vZGJ5ZVJlcXVlc3QSHAoJZmlyc3RuYW1lGAEgASgJUglmaXJzdG5hbWUSGgoIbGFzdG5hbWUYAiABKAlSCGxhc3RuYW1lIlkKD0dvb2RieWVSZXNwb25zZRIaCghmYXJld2VsbBgBIAEoCVIIZmFyZXdlbGwSKgoJbWVzc2FnZUlkGAIgASgLMgwuc2hhcmVkLlVVSURSCW1lc3NhZ2VJZDKEAQoOR29vZGJ5ZVNlcnZpY2UScgoHZ29vZGJ5ZRIyLmlvLmdpdGh1Yi5taWNyb2Nrcy5ncnBjLmdvb2RieWUudjEuR29vZGJ5ZVJlcXVlc3QaMy5pby5naXRodWIubWljcm9ja3MuZ3JwYy5nb29kYnllLnYxLkdvb2RieWVSZXNwb25zZUICUAFiBnByb3RvMw==";

      Descriptors.MethodDescriptor desc = null;
      try {
         desc = GrpcUtil.findMethodDescriptor(base64ProtobufDescriptor, "GoodbyeService", "goodbye");
      } catch (Exception e) {
         fail("No exception should be thrown while parsing protobuf descriptor and searching service");
      }

      assertNotNull(desc);
      assertEquals("io.github.microcks.grpc.goodbye.v1.GoodbyeService.goodbye", desc.getFullName());
   }

   @Test
   void testBuildTypeRegistry() {
      // This is the ExampleService from example-any-v1.proto using Any typed parameter and requiring a TypeRegistry.
      String base64ProtobufDescriptor = "Ct0BChlnb29nbGUvcHJvdG9idWYvYW55LnByb3RvEg9nb29nbGUucHJvdG9idWYiNgoDQW55EhkKCHR5cGVfdXJsGAEgASgJUgd0eXBlVXJsEhQKBXZhbHVlGAIgASgMUgV2YWx1ZUJvChNjb20uZ29vZ2xlLnByb3RvYnVmQghBbnlQcm90b1ABWiVnaXRodWIuY29tL2dvbGFuZy9wcm90b2J1Zi9wdHlwZXMvYW55ogIDR1BCqgIeR29vZ2xlLlByb3RvYnVmLldlbGxLbm93blR5cGVzYgZwcm90bzMK/AMKFWV4YW1wbGVfc2VydmljZS5wcm90bxIKZXhhbXBsZS52MRoZZ29vZ2xlL3Byb3RvYnVmL2FueS5wcm90byKdAQoORXhhbXBsZU1lc3NhZ2USQwoHcGF5bG9hZBgBIAEoCzIpLmV4YW1wbGUudjEuRXhhbXBsZU1lc3NhZ2UuRXhhbXBsZVBheWxvYWRSB3BheWxvYWQaRgoORXhhbXBsZVBheWxvYWQSNAoKcGFyYW1ldGVycxgBIAEoCzIULmdvb2dsZS5wcm90b2J1Zi5BbnlSCnBhcmFtZXRlcnMihwEKFkV4YW1wbGVSZXNwb25zZU1lc3NhZ2USUwoHcGF5bG9hZBgBIAEoCzI5LmV4YW1wbGUudjEuRXhhbXBsZVJlc3BvbnNlTWVzc2FnZS5FeGFtcGxlUmVzcG9uc2VQYXlsb2FkUgdwYXlsb2FkGhgKFkV4YW1wbGVSZXNwb25zZVBheWxvYWQiKQoTRXhhbXBsZVBhcmFtZXRlcnNWMRISCgR0ZXh0GAEgASgJUgR0ZXh0Ml8KEEV4YW1wbGVTZXJ2aWNlVjESSwoHRXhhbXBsZRIaLmV4YW1wbGUudjEuRXhhbXBsZU1lc3NhZ2UaIi5leGFtcGxlLnYxLkV4YW1wbGVSZXNwb25zZU1lc3NhZ2UiAGIGcHJvdG8z";

      TypeRegistry registry = null;
      try {
         registry = GrpcUtil.buildTypeRegistry(base64ProtobufDescriptor);
      } catch (Exception e) {
         fail("No exception should be thrown while parsing protobuf descriptor and building registry");
      }

      assertNotNull(registry);
      assertNotNull(registry.find("example.v1.ExampleParametersV1"));
      assertNotNull(registry.find("example.v1.ExampleMessage"));
      assertNotNull(registry.find("example.v1.ExampleMessage.ExamplePayload"));
      assertNotNull(registry.find("example.v1.ExampleResponseMessage"));
      assertNotNull(registry.find("example.v1.ExampleResponseMessage.ExampleResponsePayload"));
      assertNotNull(registry.find("google.protobuf.Any"));
   }
}
