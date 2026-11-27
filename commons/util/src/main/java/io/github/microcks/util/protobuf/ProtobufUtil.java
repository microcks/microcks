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
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Helper class for dealing with Protobuf messages described by a compiled descriptor set (as produced by
 * {@code protoc --descriptor_set_out --include_imports}), without requiring generated classes.
 * @author pbvittal
 */
public class ProtobufUtil {

   /** The suffix used for naming a descriptor resource after the Protobuf schema resource it was compiled from. */
   public static final String DESCRIPTOR_RESOURCE_SUFFIX = ".pbb";

   private ProtobufUtil() {
      // Private constructor to hide the implicit public one.
   }

   /**
    * Find a message descriptor into a Base64 encoded descriptor set. If {@code messageTypeName} is null or empty, the
    * first message of the main file (the last one of the set when compiled with {@code --include_imports}) is returned.
    * @param base64Descriptor A Base64 encoded {@code FileDescriptorSet}
    * @param messageTypeName  The short or fully qualified name of message type (leading '.' or '/' are ignored). May be
    *                         null.
    * @return The descriptor of the found message type
    * @throws InvalidProtocolBufferException            if the descriptor set cannot be parsed
    * @throws Descriptors.DescriptorValidationException if the descriptor set is not consistent
    * @throws IllegalArgumentException                  if no message type can be found
    */
   public static Descriptors.Descriptor findMessageDescriptor(String base64Descriptor, String messageTypeName)
         throws InvalidProtocolBufferException, Descriptors.DescriptorValidationException {
      DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet
            .parseFrom(Base64.getDecoder().decode(base64Descriptor));

      List<Descriptors.FileDescriptor> files = new ArrayList<>();
      for (DescriptorProtos.FileDescriptorProto fdp : fds.getFileList()) {
         files.add(Descriptors.FileDescriptor.buildFrom(fdp, files.toArray(new Descriptors.FileDescriptor[0]), true));
      }

      if (messageTypeName == null || messageTypeName.isBlank()) {
         if (!files.isEmpty() && !files.getLast().getMessageTypes().isEmpty()) {
            return files.getLast().getMessageTypes().getFirst();
         }
         throw new IllegalArgumentException("No message type found in Protobuf descriptor");
      }

      String typeName = messageTypeName.replaceFirst("^[./]+", "");
      List<Descriptors.Descriptor> messageTypes = new ArrayList<>();
      files.reversed().forEach(file -> collectMessageTypes(file.getMessageTypes(), messageTypes));
      // Look first for a fully qualified name match, then for a simple name match.
      for (Descriptors.Descriptor descriptor : messageTypes) {
         if (descriptor.getFullName().equals(typeName)) {
            return descriptor;
         }
      }
      for (Descriptors.Descriptor descriptor : messageTypes) {
         if (descriptor.getName().equals(typeName)) {
            return descriptor;
         }
      }
      throw new IllegalArgumentException("Message type '" + messageTypeName + "' not found in Protobuf descriptor");
   }

   /**
    * Encode a JSON representation of a message into Protobuf binary. Unknown JSON fields are rejected.
    * @param json       The JSON representation of message (as found in an example)
    * @param descriptor The descriptor of message type
    * @return The Protobuf binary encoding of message
    * @throws InvalidProtocolBufferException if JSON cannot be merged into the message type
    */
   public static byte[] jsonToProtobuf(String json, Descriptors.Descriptor descriptor)
         throws InvalidProtocolBufferException {
      DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
      JsonFormat.parser().usingTypeRegistry(buildTypeRegistry(descriptor)).merge(json, builder);
      return builder.build().toByteArray();
   }

   /**
    * Decode a Protobuf binary payload into its JSON representation.
    * @param bytes      The Protobuf binary payload
    * @param descriptor The descriptor of message type
    * @return The JSON representation of message
    * @throws InvalidProtocolBufferException if bytes cannot be parsed as the message type
    */
   public static String protobufToJson(byte[] bytes, Descriptors.Descriptor descriptor)
         throws InvalidProtocolBufferException {
      DynamicMessage message = DynamicMessage.parseFrom(descriptor, bytes);
      return JsonFormat.printer().usingTypeRegistry(buildTypeRegistry(descriptor)).print(message);
   }

   /**
    * Validate a Protobuf binary payload against a message type. Payload is considered invalid if it cannot be parsed
    * (wire type mismatch, truncated content, missing proto2 required fields) or if it holds fields whose numbers are
    * not declared by the message type (or one of its nested message types).
    * @param bytes      The Protobuf binary payload
    * @param descriptor The descriptor of message type
    * @return The list of validation errors. Empty if payload is valid.
    */
   public static List<String> getValidationErrors(byte[] bytes, Descriptors.Descriptor descriptor) {
      List<String> errors = new ArrayList<>();
      try {
         collectUnknownFields(DynamicMessage.parseFrom(descriptor, bytes), errors);
      } catch (InvalidProtocolBufferException ipbe) {
         errors.add(
               "Payload cannot be parsed as Protobuf message '" + descriptor.getFullName() + "': " + ipbe.getMessage());
      }
      return errors;
   }

   private static void collectMessageTypes(List<Descriptors.Descriptor> descriptors,
         List<Descriptors.Descriptor> messageTypes) {
      for (Descriptors.Descriptor descriptor : descriptors) {
         messageTypes.add(descriptor);
         collectMessageTypes(descriptor.getNestedTypes(), messageTypes);
      }
   }

   private static void collectUnknownFields(Message message, List<String> errors) {
      for (Integer fieldNumber : message.getUnknownFields().asMap().keySet()) {
         errors.add("Field number " + fieldNumber + " is not declared in Protobuf message '"
               + message.getDescriptorForType().getFullName() + "'");
      }
      for (Map.Entry<Descriptors.FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
         if (field.getKey().getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
            if (field.getKey().isRepeated()) {
               for (Object item : (List<?>) field.getValue()) {
                  collectUnknownFields((Message) item, errors);
               }
            } else {
               collectUnknownFields((Message) field.getValue(), errors);
            }
         }
      }
   }

   private static TypeRegistry buildTypeRegistry(Descriptors.Descriptor descriptor) {
      TypeRegistry.Builder builder = TypeRegistry.newBuilder().add(descriptor.getFile().getMessageTypes());
      descriptor.getFile().getDependencies().forEach(dependency -> builder.add(dependency.getMessageTypes()));
      return builder.build();
   }
}
