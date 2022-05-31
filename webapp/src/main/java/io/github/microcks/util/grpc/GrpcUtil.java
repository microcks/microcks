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

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.MethodDescriptor;
import io.grpc.services.BinaryLogProvider;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Helper class containing utility methods related to Grpc/Protobuf decriptors.
 * @author laurent
 */
public class GrpcUtil {

   /**
    * Find a Protobuf method descriptor using a base64 encoded representation of the proto descriptor + service and method name.
    * @param base64ProtobufDescriptor The encoded representation of proto descriptor as produced by protoc.
    * @param serviceName The name of the service to get method for.
    * @param methodName The name of the method.
    * @return A Protobuf MethodDescriptor
    * @throws UnsupportedEncodingException If base64 content is not encoded in uTF-8
    * @throws InvalidProtocolBufferException If representation is not understood as protobuf descriptor.
    * @throws Descriptors.DescriptorValidationException If included FileDescriptor cannot be validated.
    */
   public static Descriptors.MethodDescriptor findMethodDescriptor(String base64ProtobufDescriptor, String serviceName, String methodName)
         throws UnsupportedEncodingException, InvalidProtocolBufferException, Descriptors.DescriptorValidationException{

      // Protobuf binary descriptor has been encoded in base64 to be stored as a string.
      // Decode it and recreate DescriptorProtos objects.
      byte[] decodedBinaryPB = Base64.getDecoder().decode(base64ProtobufDescriptor.getBytes("UTF-8"));
      DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(decodedBinaryPB);

      Descriptors.ServiceDescriptor sd = null;
      if (fds.getFileCount() > 1) {
         // Build dependencies.
         List<Descriptors.FileDescriptor> dependencies = new ArrayList<>();
         for (int i=0; i<fds.getFileCount(); i++) {
            // Build descriptor and add to dependencies.
            Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(fds.getFile(i),
                  dependencies.toArray(new Descriptors.FileDescriptor[dependencies.size()]), true);
            dependencies.add(fd);
            // Search for service.
            sd = fd.findServiceByName(serviceName);
            if (sd != null) {
               break;
            }
         }
      } else {
         Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(fds.getFile(0),
               new Descriptors.FileDescriptor[]{}, true);
         sd = fd.findServiceByName(serviceName);
      }

      return sd.findMethodByName(methodName);
   }

   /**
    * Build a generic GRPC Unary Method descriptor (using byte[] as input and byte[] as output.
    * @param fullMethodName The GRPC method full name (service fqdn / method)
    * @return A new MethodDescriptor using a byte array marshaller.
    */
   public static MethodDescriptor<byte[], byte[]> buildGenericUnaryMethodDescriptor(String fullMethodName) {
      return MethodDescriptor.newBuilder(BinaryLogProvider.BYTEARRAY_MARSHALLER, BinaryLogProvider.BYTEARRAY_MARSHALLER)
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethodName)
            .build();
   }
}
