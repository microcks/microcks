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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Based on the <a href="http://www.jsonrpc.org/specification">JSON-RPC 2.0 specification</a> and the
 * <a href= "https://github.com/modelcontextprotocol/specification/blob/main/schema/2025-03-26/schema.ts">Model Context
 * Protocol Schema</a>.
 * @author laurent
 */
public class McpSchema {

   public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of("2024-11-05", "2025-03-26", "2025-06-18");

   public static final String JSONRPC_VERSION = "2.0";

   // ---------------------------
   // Method Names
   // ---------------------------

   // Lifecycle Methods
   public static final String METHOD_INITIALIZE = "initialize";
   public static final String METHOD_NOTIFICATION_INITIALIZED = "notifications/initialized";
   public static final String METHOD_PING = "ping";

   // Tool Methods
   public static final String METHOD_TOOLS_LIST = "tools/list";
   public static final String METHOD_TOOLS_CALL = "tools/call";
   public static final String METHOD_NOTIFICATION_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";

   // Resources Methods
   public static final String METHOD_RESOURCES_LIST = "resources/list";
   public static final String METHOD_RESOURCES_READ = "resources/read";
   public static final String METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
   public static final String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";
   public static final String METHOD_RESOURCES_SUBSCRIBE = "resources/subscribe";
   public static final String METHOD_RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";

   // Prompt Methods
   public static final String METHOD_PROMPT_LIST = "prompts/list";
   public static final String METHOD_PROMPT_GET = "prompts/get";
   public static final String METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";

   // Logging Methods
   public static final String METHOD_LOGGING_SET_LEVEL = "logging/setLevel";
   public static final String METHOD_NOTIFICATION_MESSAGE = "notifications/message";

   // Roots Methods
   public static final String METHOD_ROOTS_LIST = "roots/list";
   public static final String METHOD_NOTIFICATION_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";

   // Sampling Methods
   public static final String METHOD_SAMPLING_CREATE_MESSAGE = "sampling/createMessage";

   private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


   // ---------------------------
   // JSON-RPC Error Codes
   // ---------------------------
   /**
    * Standard error codes used in MCP JSON-RPC responses.
    */
   public static final class ErrorCodes {

      /** Invalid JSON was received by the server. */
      public static final int PARSE_ERROR = -32700;

      /** The JSON sent is not a valid Request object. */
      public static final int INVALID_REQUEST = -32600;

      /** The method does not exist / is not available. */
      public static final int METHOD_NOT_FOUND = -32601;

      /** Invalid method parameter(s). */
      public static final int INVALID_PARAMS = -32602;

      /** Internal JSON-RPC error. */
      public static final int INTERNAL_ERROR = -32603;

   }

   /** JSON-RPC Message Types. */
   public sealed interface JSONRPCMessage permits JSONRPCRequest, JSONRPCNotification, JSONRPCResponse {
      String jsonrpc();
   }

   // spotless:off
   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record JSONRPCRequest(
         @JsonProperty("jsonrpc") String jsonrpc,
         @JsonProperty("method") String method,
         @JsonProperty("id") Object id,
         @JsonProperty("params") Object params) implements JSONRPCMessage {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record JSONRPCNotification(
         @JsonProperty("jsonrpc") String jsonrpc, @
         JsonProperty("method") String method,
         @JsonProperty("params") Map<String, Object> params) implements JSONRPCMessage {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record JSONRPCResponse(
         @JsonProperty("jsonrpc") String jsonrpc,
         @JsonProperty("id") Object id,
         @JsonProperty("result") Object result,
         @JsonProperty("error") JSONRPCError error) implements JSONRPCMessage {

      @JsonInclude(JsonInclude.Include.NON_ABSENT)
      @JsonIgnoreProperties(ignoreUnknown = true)
      public record JSONRPCError(
            @JsonProperty("code") int code,
            @JsonProperty("message") String message,
            @JsonProperty("data") Object data) {
      }
   }
   // spotless:on

   // spotless:off
   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record Implementation(
         @JsonProperty("name") String name,
         @JsonProperty("version") String version) {
   }

   public enum Role {
      @JsonProperty("user") USER,
      @JsonProperty("assistant") ASSISTANT
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record ClientCapabilities(
         @JsonProperty("experimental") Map<String, Object> experimental,
         @JsonProperty("roots") ClientCapabilities.RootCapabilities roots,
         @JsonProperty("sampling") ClientCapabilities.Sampling sampling) {

      @JsonInclude(JsonInclude.Include.NON_ABSENT)
      @JsonIgnoreProperties(ignoreUnknown = true)
      public record RootCapabilities(
            @JsonProperty("listChanged") Boolean listChanged) {
      }

      @JsonInclude(JsonInclude.Include.NON_ABSENT)
      public record Sampling() {
      }
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record ServerCapabilities(
         @JsonProperty("experimental") Map<String, Object> experimental,
         @JsonProperty("logging") ServerCapabilities.LoggingCapabilities logging,
         @JsonProperty("prompts") ServerCapabilities.PromptCapabilities prompts,
         @JsonProperty("resources") ServerCapabilities.ResourceCapabilities resources,
         @JsonProperty("tools") ServerCapabilities.ToolCapabilities tools) {


      @JsonInclude(JsonInclude.Include.NON_ABSENT)
      public record LoggingCapabilities() {
      }

      @JsonInclude(JsonInclude.Include.NON_ABSENT)
      public record PromptCapabilities(
            @JsonProperty("listChanged") Boolean listChanged) {
      }

      @JsonInclude(JsonInclude.Include.NON_ABSENT)
      public record ResourceCapabilities(
            @JsonProperty("subscribe") Boolean subscribe,
            @JsonProperty("listChanged") Boolean listChanged) {
      }

      @JsonInclude(JsonInclude.Include.NON_ABSENT)
      public record ToolCapabilities(
            @JsonProperty("listChanged") Boolean listChanged) {
      }
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record ModelPreferences(
         @JsonProperty("hints") List<ModelHint> hints,
         @JsonProperty("costPriority") Double costPriority,
         @JsonProperty("speedPriority") Double speedPriority,
         @JsonProperty("intelligencePriority") Double intelligencePriority) {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record ModelHint(@JsonProperty("name") String name) {
      public static ModelHint of(String name) {
         return new ModelHint(name);
      }
   }
   
   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record SamplingMessage(
         @JsonProperty("role") Role role,
         @JsonProperty("content") Content content) {
   }
   // spotless:on

   /** MCP Request Types. */
   public sealed interface Request permits InitializeRequest, CallToolRequest, CreateMessageRequest {

   }

   // spotless:off
   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record InitializeRequest(
         @JsonProperty("protocolVersion") String protocolVersion,
         @JsonProperty("capabilities") ClientCapabilities capabilities,
         @JsonProperty("clientInfo") Implementation clientInfo) implements Request {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record InitializeResult(
        @JsonProperty("protocolVersion") String protocolVersion,
        @JsonProperty("capabilities") ServerCapabilities capabilities,
        @JsonProperty("serverInfo") Implementation serverInfo,
        @JsonProperty("instructions") String instructions) {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record ListToolsResult(
         @JsonProperty("tools") List<Tool> tools,
         @JsonProperty("nextCursor") String nextCursor) {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record CallToolRequest(
         @JsonProperty("name") String name,
         @JsonProperty("arguments") Map<String, Object> arguments) implements Request {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record CallToolResult(
         @JsonProperty("content") List<Content> content,
         @JsonProperty("isError") Boolean isError) {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record CreateMessageRequest(
        @JsonProperty("messages") List<SamplingMessage> messages,
        @JsonProperty("modelPreferences") ModelPreferences modelPreferences,
        @JsonProperty("systemPrompt") String systemPrompt,
        @JsonProperty("includeContext") CreateMessageRequest.ContextInclusionStrategy includeContext,
        @JsonProperty("temperature") Double temperature,
        @JsonProperty("maxTokens") int maxTokens,
        @JsonProperty("stopSequences") List<String> stopSequences,
        @JsonProperty("metadata") Map<String, Object> metadata) implements Request {

      public enum ContextInclusionStrategy {
            @JsonProperty("none") NONE,
            @JsonProperty("thisServer") THIS_SERVER,
            @JsonProperty("allServers") ALL_SERVERS
      }
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record CreateMessageResult(
         @JsonProperty("role") Role role,
         @JsonProperty("content") Content content,
         @JsonProperty("model") String model,
         @JsonProperty("stopReason") CreateMessageResult.StopReason stopReason) {

      public enum StopReason {
            @JsonProperty("endTurn") END_TURN,
            @JsonProperty("stopSequence") STOP_SEQUENCE,
            @JsonProperty("maxTokens") MAX_TOKENS
      }
   }
   // spotless:on

   /** Tools model. */
   // spotless:off
   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record Tool(
         @JsonProperty("name") String name,
         @JsonProperty("description") String description,
         @JsonProperty("inputSchema") JsonSchema inputSchema) {

      public Tool(String name, String description, String schema) {
         this(name, description, parseSchema(schema));
      }
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record JsonSchema(
         @JsonProperty("type") String type,
         @JsonProperty("properties") Map<String, Object> properties,
         @JsonProperty("required") List<String> required,
         @JsonProperty("additionalProperties") Boolean additionalProperties) {
   }

   private static JsonSchema parseSchema(String schema) {
      try {
         return OBJECT_MAPPER.readValue(schema, JsonSchema.class);
      }
      catch (IOException e) {
         throw new IllegalArgumentException("Invalid schema: " + schema, e);
      }
   }
   // spotless:on

   /** Result Content Types. */
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
   @JsonSubTypes({ @JsonSubTypes.Type(value = TextContent.class, name = "text"),
         @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
         @JsonSubTypes.Type(value = EmbeddedResource.class, name = "resource") })
   public sealed interface Content permits TextContent, ImageContent, EmbeddedResource {

      default String type() {
         if (this instanceof McpSchema.TextContent) {
            return "text";
         } else if (this instanceof McpSchema.ImageContent) {
            return "image";
         } else if (this instanceof McpSchema.EmbeddedResource) {
            return "resource";
         }
         throw new IllegalArgumentException("Unknown content type: " + this);
      }
   }

   // spotless:off
   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record TextContent(
         @JsonProperty("audience") List<Role> audience,
         @JsonProperty("priority") Double priority,
         @JsonProperty("text") String text) implements Content {

      public TextContent(String content) {
         this(null, null, content);
      }
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record ImageContent(
         @JsonProperty("audience") List<Role> audience,
         @JsonProperty("priority") Double priority,
         @JsonProperty("data") String data,
         @JsonProperty("mimeType") String mimeType) implements Content {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record EmbeddedResource(
        @JsonProperty("audience") List<Role> audience,
        @JsonProperty("priority") Double priority,
        @JsonProperty("resource") ResourceContents resource) implements Content {
   }

   @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, include = JsonTypeInfo.As.PROPERTY)
   @JsonSubTypes({ @JsonSubTypes.Type(value = TextResourceContents.class, name = "text"),
         @JsonSubTypes.Type(value = BlobResourceContents.class, name = "blob") })
   public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {
      String uri();
      String mimeType();
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record TextResourceContents(
         @JsonProperty("uri") String uri,
         @JsonProperty("mimeType") String mimeType,
         @JsonProperty("text") String text) implements ResourceContents {
   }

   @JsonInclude(JsonInclude.Include.NON_ABSENT)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record BlobResourceContents(
         @JsonProperty("uri") String uri,
         @JsonProperty("mimeType") String mimeType,
         @JsonProperty("blob") String blob) implements ResourceContents {
   }
   // spotless:on
}
