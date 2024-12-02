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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import io.github.microcks.util.script.HttpHeadersStringToStringsMap;
import io.github.microcks.util.script.StringToStringsMap;
import io.grpc.Metadata;

/**
 * This is a test case for GrpcMetadataUtil.
 */
public class GrpcMetadataUtilTest {
   @Test
   void testMetadataWithMultipleEntries() {
      Metadata metadata = new Metadata();
      metadata.put(Metadata.Key.of("foo1", Metadata.ASCII_STRING_MARSHALLER), "bar");
      metadata.put(Metadata.Key.of("foo2", Metadata.ASCII_STRING_MARSHALLER), "bar");

      StringToStringsMap headers = null;
      try {
         headers = GrpcMetadataUtil.convertToMap(metadata);
      } catch (Exception e) {
         fail("No exception should be thrown while parsing grpc metadata");
      }
      StringToStringsMap expectedHeaders = new HttpHeadersStringToStringsMap();
      expectedHeaders.put("foo1", "bar");
      expectedHeaders.put("foo2", "bar");
      assertEquals(expectedHeaders, headers);
   }

   @Test
   void testMetadataWithKeyMultipleTimes() {
      Metadata metadata = new Metadata();
      metadata.put(Metadata.Key.of("foo", Metadata.ASCII_STRING_MARSHALLER), "bar1");
      metadata.put(Metadata.Key.of("foo", Metadata.ASCII_STRING_MARSHALLER), "bar2");

      StringToStringsMap headers = null;
      try {
         headers = GrpcMetadataUtil.convertToMap(metadata);
      } catch (Exception e) {
         fail("No exception should be thrown while parsing grpc metadata");
      }
      StringToStringsMap expectedHeaders = new StringToStringsMap();
      expectedHeaders.put("foo", "bar1");
      expectedHeaders.put("foo", "bar2");
      assertEquals(expectedHeaders, headers);
   }

   @Test
   void testMetadataWithEmptyMetadata() {
      Metadata metadata = new Metadata();

      StringToStringsMap headers = null;
      try {
         headers = GrpcMetadataUtil.convertToMap(metadata);
      } catch (Exception e) {
         fail("No exception should be thrown while parsing empty grpc metadata");
      }
      StringToStringsMap expectedHeaders = new StringToStringsMap();
      assertEquals(expectedHeaders, headers);
   }

   @Test
   void testMetadataWithStringMetadata() {
      Metadata metadata = new Metadata();
      metadata.put(Metadata.Key.of("foo1", Metadata.ASCII_STRING_MARSHALLER), "bar");

      StringToStringsMap headers = null;
      try {
         headers = GrpcMetadataUtil.convertToMap(metadata);
      } catch (Exception e) {
         fail("No exception should be thrown while parsing grpc metadata");
      }
      StringToStringsMap expectedHeaders = new StringToStringsMap();
      expectedHeaders.put("foo1", "bar");
      assertEquals(expectedHeaders, headers);
   }

   @Test
   void testMetadataWithBinaryMetadata() {
      Metadata metadata = new Metadata();
      metadata.put(Metadata.Key.of("foo1-bin", Metadata.BINARY_BYTE_MARSHALLER), "bar".getBytes());

      StringToStringsMap headers = null;
      try {
         headers = GrpcMetadataUtil.convertToMap(metadata);
      } catch (Exception e) {
         fail("No exception should be thrown while parsing grpc metadata");
      }
      StringToStringsMap expectedHeaders = new StringToStringsMap();
      expectedHeaders.put("foo1-bin", "bar");
      assertEquals(expectedHeaders, headers);
   }
}
