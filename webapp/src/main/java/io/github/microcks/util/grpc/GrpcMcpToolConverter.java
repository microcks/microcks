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
import com.google.protobuf.Descriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
      Map<String, Object> properties = new HashMap<>();
      List<String> requiredProperties = new ArrayList<>();

      try {
         if (sd == null) {
            sd = GrpcUtil.findServiceDescriptor(resource.getContent(), service.getName());
         }

         Descriptors.MethodDescriptor md = sd.findMethodByName(operation.getName());
         if (md.getInputType() != null) {
            // Get the input type descriptor.
            Descriptors.Descriptor inputType = md.getInputType();
            for (Descriptors.FieldDescriptor fd : inputType.getFields()) {
               // Get the field name and type.
               String fieldName = fd.getName();
               String fieldType = fd.getType().name();

               // Check if the field is required.
               if (fd.isRequired()) {
                  requiredProperties.add(fieldName);
               }
               // Check if the field is a scalar type.
               if (isScalarType(fd.getType())) {
                  properties.put(fieldName, Map.of("type", toMcpJsonType(fd.getType())));
               } else {
                  properties.put(fieldName, new McpSchema.JsonSchema(fieldType, null, null, false));
               }
            }
         }
      } catch (Exception e) {
         log.error("Exception while trying to get input schema", e);
      }
      return new McpSchema.JsonSchema("object", properties, requiredProperties, false);
   }

   @Override
   public Response getCallResponse(Operation operation, McpSchema.CallToolRequest request) {
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

   /** Defines is a protobuf message field type is a scalar type. */
   private static boolean isScalarType(Descriptors.FieldDescriptor.Type fieldType) {
      return fieldType != Descriptors.FieldDescriptor.Type.MESSAGE
            && fieldType != Descriptors.FieldDescriptor.Type.GROUP
            && fieldType != Descriptors.FieldDescriptor.Type.BYTES;
   }

   /** Convert a scalar Field Protobuf type into a MCP Json compatible one. */
   private static String toMcpJsonType(Descriptors.FieldDescriptor.Type fieldType) {
      switch (fieldType) {
         case DOUBLE:
         case FLOAT:
         case INT64:
         case UINT64:
         case INT32:
         case FIXED64:
         case FIXED32:
            return "number";
         case BOOL:
            return "boolean";
         case STRING:
         case BYTES:
         default:
            return "string";
      }
   }
}
