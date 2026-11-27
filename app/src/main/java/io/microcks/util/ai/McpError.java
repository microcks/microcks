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
package io.github.microcks.util.ai;

/**
 * Utility class for handling errors from MCP Tool invocations.
 * @author laurent
 */
public class McpError extends RuntimeException {

   private McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError;

   /**
    * Constructor for creating a new McpError from a JSONRPCError.
    * @param jsonRpcError The JSONRPCError to create the McpError from.
    */
   public McpError(McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError) {
      super(jsonRpcError.message());
      this.jsonRpcError = jsonRpcError;
   }

   /**
    * Constructor for creating a new McpError from an error object string representation.
    * @param error The error object to create the McpError from.
    */
   public McpError(Object error) {
      super(error.toString());
   }

   /**
    * Get the JSONRPCError associated with this McpError.
    * @return The JSONRPCError associated with this McpError.
    */
   public McpSchema.JSONRPCResponse.JSONRPCError getJsonRpcError() {
      return jsonRpcError;
   }
}
