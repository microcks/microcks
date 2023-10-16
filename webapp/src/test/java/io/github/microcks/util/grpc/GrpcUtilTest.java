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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * This is a test case for GrpcUtil class.
 * @author laurent
 */
public class GrpcUtilTest {

   @Test
   public void testFindMethodDescriptor() {
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
   public void testFindMethodDescriptorWithDependency() {
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
}
