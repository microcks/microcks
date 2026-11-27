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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.util.ai.McpToolConverter;
import io.github.microcks.web.GrpcInvocationProcessor;
import io.github.microcks.web.GrpcResponseResult;
import io.github.microcks.web.MockInvocationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Descriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.github.microcks.util.JsonSchemaValidator.*;

/**
 * Implementation of McpToolConverter for gRPC services.
 * @author laurent
 */
public class GrpcMcpToolConverter extends McpToolConverter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(GrpcMcpToolConverter.class);

   private final GrpcInvocationProcessor invocationProcessor;
   private final ObjectMapper mapper;

   private Descriptors.ServiceDescriptor sd;

   /**
    * Build a new instance of GrpcMcpToolConverter.
    * @param service             The service to which this converter is attached
    * @param resource            The resource used for OpenAPI service conversion
    * @param invocationProcessor The invocation processor to use for processing the call
    * @param mapper              The ObjectMapper to use for JSON serialization
    */
   public GrpcMcpToolConverter(Service service, Resource resource, GrpcInvocationProcessor invocationProcessor,
         ObjectMapper mapper) {
      super(service, resource);
      this.invocationProcessor = invocationProcessor;
      this.mapper = mapper;
   }

   @Override
   public String getToolDescription(Operation operation) {
      return null;
   }

   @Override
   public McpSchema.JsonSchema getInputSchema(Operation operation) {
      ObjectNode inputSchemaNode = mapper.createObjectNode();
      ObjectNode schemaPropertiesNode = mapper.createObjectNode();
      ArrayNode requiredPropertiesNode = mapper.createArrayNode();

      // Initialize input schema with empty object.
      inputSchemaNode.put(JSON_SCHEMA_TYPE_ELEMENT, JSON_SCHEMA_OBJECT_TYPE);
      inputSchemaNode.set(JSON_SCHEMA_PROPERTIES_ELEMENT, schemaPropertiesNode);
      inputSchemaNode.set(JSON_SCHEMA_REQUIRED_ELEMENT, requiredPropertiesNode);
      inputSchemaNode.put(JSON_SCHEMA_ADD_PROPERTIES_ELEMENT, false);
      try {
         if (sd == null) {
            sd = GrpcUtil.findServiceDescriptor(resource.getContent(), service.getName());
         }

         Descriptors.MethodDescriptor md = sd.findMethodByName(operation.getName());
         if (md.getInputType() != null) {
            // Visit the input type descriptor.
            visitDescriptor(md.getInputType(), schemaPropertiesNode, requiredPropertiesNode);
         }
      } catch (Exception e) {
         log.error("Exception while trying to get input schema", e);
      }
      return mapper.convertValue(inputSchemaNode, McpSchema.JsonSchema.class);
   }

   @Override
   public Response getCallResponse(Operation operation, McpSchema.CallToolRequest request,
         Map<String, List<String>> headers) {
      // Build a mock invocation context needed for the invocation processor.
      MockInvocationContext ic = new MockInvocationContext(service, operation, null);

      try {
         // De-serialize remaining arguments as the request body.
         String body = mapper.writeValueAsString(request.arguments());

         // Execute the invocation processor.
         GrpcResponseResult result = invocationProcessor.processInvocation(ic, System.currentTimeMillis(), body);

         // Build a Microcks Response from the result.
         Response response = new Response();
         if (result.content() != null) {
            response.setContent(result.content());
         }
         if (result.isError()) {
            response.setFault(true);
            response.setStatus("500");
            response.setContent(result.errorDescription());
         }
         return response;
      } catch (Exception e) {
         log.error("Exception while processing the MCP call invocation", e);
      }
      return null;
   }

   /** Visit a protobuf message descriptor and extract its properties. */
   private void visitDescriptor(Descriptors.Descriptor inputType, ObjectNode propertiesNode,
         ArrayNode requiredPropertiesNode) {
      for (Descriptors.FieldDescriptor fd : inputType.getFields()) {

         // Get the field name and type.
         String fieldName = fd.getName();

         // Check if the field is required.
         if (fd.isRequired()) {
            requiredPropertiesNode.add(fieldName);
         }

         // Check if the field is an array/a repeated field.
         if (fd.isRepeated()) {
            // We must convert to a type array.
            ObjectNode arraySchemaNode = mapper.createObjectNode();

            arraySchemaNode.put(JSON_SCHEMA_TYPE_ELEMENT, JSON_SCHEMA_ARRAY_TYPE);
            propertiesNode.set(fieldName, arraySchemaNode);

            if (isMessageType(fd.getType())) {
               ObjectNode subschemaNode = mapper.createObjectNode();
               ObjectNode subitemsNode = mapper.createObjectNode();
               ArrayNode requiredSubitemsPropertiesNode = mapper.createArrayNode();

               visitDescriptor(fd.getMessageType(), subitemsNode, requiredSubitemsPropertiesNode);

               // Add the required properties to the subschema.
               subschemaNode.put(JSON_SCHEMA_TYPE_ELEMENT, JSON_SCHEMA_OBJECT_TYPE);
               subschemaNode.set(JSON_SCHEMA_PROPERTIES_ELEMENT, subitemsNode);

               // Add the items definition to the current property.
               arraySchemaNode.set(JSON_SCHEMA_ITEMS_ELEMENT, subschemaNode);
               propertiesNode.set(fieldName, arraySchemaNode);
            } else if (isEnumType(fd.getType())) {
               arraySchemaNode.set(JSON_SCHEMA_ITEMS_ELEMENT, buildEnumTypeNode(fd));
            } else if (isScalarType(fd.getType())) {
               arraySchemaNode.set(JSON_SCHEMA_ITEMS_ELEMENT, buildScalarTypeNode(fd));
            }
         } else {
            // Check if the field is an object/message type.
            if (isMessageType(fd.getType())) {
               // Initialize a new subschema node we must visit to resolve message fields.
               ObjectNode subschemaNode = mapper.createObjectNode();
               ObjectNode subpropertiesNode = mapper.createObjectNode();
               ArrayNode requiredSubpropertiesNode = mapper.createArrayNode();

               subschemaNode.put(JSON_SCHEMA_TYPE_ELEMENT, JSON_SCHEMA_OBJECT_TYPE);
               subschemaNode.set(JSON_SCHEMA_PROPERTIES_ELEMENT, subpropertiesNode);
               subschemaNode.set(JSON_SCHEMA_REQUIRED_ELEMENT, requiredSubpropertiesNode);
               subschemaNode.put(JSON_SCHEMA_ADD_PROPERTIES_ELEMENT, false);
               propertiesNode.set(fieldName, subschemaNode);

               visitDescriptor(fd.getMessageType(), subpropertiesNode, requiredSubpropertiesNode);
            } else if (isEnumType(fd.getType())) {
               propertiesNode.set(fieldName, buildEnumTypeNode(fd));
            } else if (isScalarType(fd.getType())) {
               propertiesNode.set(fieldName, buildScalarTypeNode(fd));
            }
         }
      }
   }

   /** Defines is a protobuf message field type is an message type. */
   private static boolean isMessageType(Descriptors.FieldDescriptor.Type fieldType) {
      return fieldType == Descriptors.FieldDescriptor.Type.MESSAGE
            || fieldType == Descriptors.FieldDescriptor.Type.GROUP;
   }

   /** Defines is a protobuf message field type is an enum type. */
   private static boolean isEnumType(Descriptors.FieldDescriptor.Type fieldType) {
      return fieldType == Descriptors.FieldDescriptor.Type.ENUM;
   }

   /** Defines is a protobuf message field type is a scalar type. */
   private static boolean isScalarType(Descriptors.FieldDescriptor.Type fieldType) {
      return fieldType != Descriptors.FieldDescriptor.Type.MESSAGE
            && fieldType != Descriptors.FieldDescriptor.Type.GROUP
            && fieldType != Descriptors.FieldDescriptor.Type.BYTES;
   }

   /** Build a leaf node that is an enum type. */
   private ObjectNode buildEnumTypeNode(Descriptors.FieldDescriptor fd) {
      ObjectNode subschemaNode = mapper.createObjectNode();
      ArrayNode enumsArrayNode = mapper.createArrayNode();

      subschemaNode.put("type", toMcpJsonType(fd.getType()));
      subschemaNode.set("enum", enumsArrayNode);

      // Put the enum values in enum array.
      for (Descriptors.EnumValueDescriptor evd : fd.getEnumType().getValues()) {
         enumsArrayNode.add(evd.getName());
      }
      return subschemaNode;
   }

   /** Build a leaf node that is a scalar type. */
   private ObjectNode buildScalarTypeNode(Descriptors.FieldDescriptor fd) {
      ObjectNode subschemaNode = mapper.createObjectNode();
      subschemaNode.put("type", toMcpJsonType(fd.getType()));
      return subschemaNode;
   }

   /** Convert a scalar Field Protobuf type into a MCP Json compatible one. */
   private static String toMcpJsonType(Descriptors.FieldDescriptor.Type fieldType) {
      switch (fieldType) {
         case DOUBLE, FLOAT, INT64, UINT64, INT32, FIXED64, FIXED32:
            return "number";
         case BOOL:
            return "boolean";
         case STRING, BYTES:
         default:
            return "string";
      }
   }
}
