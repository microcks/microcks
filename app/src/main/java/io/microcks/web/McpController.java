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
package io.microcks.web;

import io.microcks.domain.Header;
import io.microcks.domain.Operation;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.util.ai.McpError;
import io.microcks.util.ai.McpSchema;
import io.microcks.util.ai.McpToolConverter;
import io.microcks.web.mcp.GraphQLMcpToolConverter;
import io.microcks.web.mcp.GrpcMcpToolConverter;
import io.microcks.web.mcp.OpenAPIMcpToolConverter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This controller handles the MCP protocol with HTTP/SEE transport to use Microcks mocks as tools.
 * @author laurent
 */
@ApplicationScoped
@Path("/mcp")
public class McpController {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(McpController.class);

   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;
   private final RestInvocationProcessor restInvocationProcessor;
   private final GrpcInvocationProcessor grpcInvocationProcessor;
   private final GraphQLInvocationProcessor graphQLInvocationProcessor;

   private final ConcurrentHashMap<String, SseTransportChannel> channelsBySessionId = new ConcurrentHashMap<>();
   private final ObjectMapper mapper = new ObjectMapper();

   /**
    * Build a McpController with required dependencies.
    * @param serviceRepository          The repository to access services definitions
    * @param resourceRepository         The repository to access resources definitions
    * @param restInvocationProcessor    The invocation processor to apply REST mocks dispatching logic
    * @param grpcInvocationProcessor    The invocation processor to apply GRPC mocks dispatching logic
    * @param graphQLInvocationProcessor The invocation processor to apply GraphQL mocks dispatching logic
    */
   @Inject
   public McpController(ServiceRepository serviceRepository, ResourceRepository resourceRepository,
         RestInvocationProcessor restInvocationProcessor, GrpcInvocationProcessor grpcInvocationProcessor,
         GraphQLInvocationProcessor graphQLInvocationProcessor) {
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
      this.restInvocationProcessor = restInvocationProcessor;
      this.grpcInvocationProcessor = grpcInvocationProcessor;
      this.graphQLInvocationProcessor = graphQLInvocationProcessor;
   }

   /**
    * Handle the initialization of a SSE connection for the MCP protocol.
    * @param serviceName The name of the service to connect to.
    * @param version     The version of the service to connect to.
    * @return The SSE emitter to use for the connection.
    */
   @GET
   @Path("/{service}/{version}/sse")
   @Produces(MediaType.SERVER_SENT_EVENTS)
   public Multi<OutboundSseEvent> handleSSE(@PathParam("service") String serviceName,
         @PathParam("version") String version, @Context Sse sse) {
      log.info("Handling a Mcp SSE for service {} and version {}", serviceName, version);

      String sessionId = UUID.randomUUID().toString();
      log.debug("Creating new SSE connection for session: {}", sessionId);

      // If serviceName was encoded with '+' instead of '%20', remove them.
      final String serviceNameFinal = serviceName.replace('+', ' ');

      return Multi.createFrom().<OutboundSseEvent>emitter(emitter -> {
         channelsBySessionId.put(sessionId, new SseTransportChannel(sessionId, emitter, sse));

         // Same 600s lifetime as the 1.x SSE emitter.
         Cancellable timeout = Uni.createFrom().voidItem().onItem().delayIt().by(Duration.ofSeconds(600)).subscribe()
               .with(v -> {
                  log.debug("SSE connection timed out for session: {}", sessionId);
                  emitter.complete();
               });
         emitter.onTermination(() -> {
            log.debug("SSE connection completed for session: {}", sessionId);
            timeout.cancel();
            channelsBySessionId.remove(sessionId);
         });

         emitter.emit(sse.newEventBuilder().name("endpoint").id(sessionId)
               .data("/mcp/" + serviceNameFinal + "/" + version + "/message?sessionId=" + sessionId).build());
      });
   }

   /**
    * Handle a MCP message request.
    * @param serviceName The name of the service to connect to.
    * @param version     The version of the service to connect to.
    * @param sessionId   The MCP session ID of the connection.
    * @param request     The MCP request to handle.
    * @return The response entity.
    */
   @POST
   @Path("/{service}/{version}/message")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.SERVER_SENT_EVENTS)
   @RunOnVirtualThread
   public jakarta.ws.rs.core.Response handleMessage(@PathParam("service") String serviceName,
         @PathParam("version") String version, @QueryParam("sessionId") String sessionId,
         McpSchema.JSONRPCRequest request, @Context HttpHeaders headers) {

      log.info("Handling a {} Mcp request for service {} and version {}", request.method(), serviceName, version);

      // Check session is known and valid.
      SseTransportChannel channel = sessionId == null ? null : channelsBySessionId.get(sessionId);
      if (channel == null) {
         log.debug("Session {} not found", sessionId);
         return badRequest(new McpError("Session not found: " + sessionId));
      }

      McpSchema.JSONRPCResponse response = null;
      try {
         response = handleMcpRequest(serviceName, version, request, headers);
      } catch (McpError e) {
         return badRequest(e);
      }
      sendSseMessage(channel, response);

      return jakarta.ws.rs.core.Response.ok().build();
   }

   private static jakarta.ws.rs.core.Response badRequest(McpError error) {
      return jakarta.ws.rs.core.Response.status(400).type(MediaType.APPLICATION_JSON).entity(error).build();
   }

   /**
    * Reserved for future usage when HTTP Streamable transport specification will be frozen and implemented.
    * @param serviceName The name of the service to connect to.
    * @param version     The version of the service to connect to.
    * @param request     The MCP request to handle.
    * @return The response entity.
    */
   @POST
   @Path("/{service}/{version}")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   @RunOnVirtualThread
   public jakarta.ws.rs.core.Response handleHttpStreamable(@PathParam("service") String serviceName,
         @PathParam("version") String version, McpSchema.JSONRPCRequest request, @Context HttpHeaders headers) {

      log.info("Handling a Mcp Http streamable call for service {} and version {}", serviceName, version);

      try {
         McpSchema.JSONRPCResponse response = handleMcpRequest(serviceName, version, request, headers);
         return jakarta.ws.rs.core.Response.ok(response).build();
      } catch (McpError e) {
         return badRequest(e);
      }
   }

   /** Logic of handling Mcp Request. */
   private McpSchema.JSONRPCResponse handleMcpRequest(String serviceName, String version,
         McpSchema.JSONRPCRequest request, HttpHeaders headers) throws McpError {
      // If serviceName was encoded with '+' instead of '%20', remove them.
      if (serviceName.contains("+")) {
         serviceName = serviceName.replace('+', ' ');
      }
      // Find matching service.
      Service service = serviceRepository.findByNameAndVersion(serviceName, version);
      if (service == null) {
         log.debug("Service {}:{} not found", serviceName, version);
         throw new McpError("Invalid service name or version");
      }

      Object result = null;
      switch (request.method()) {
         case McpSchema.METHOD_INITIALIZE -> {
            result = handleInitializeRequest(request, service);
         }
         case McpSchema.METHOD_TOOLS_LIST -> {
            result = handleToolsListRequest(request, service);
         }
         case McpSchema.METHOD_TOOLS_CALL -> {
            result = handleToolsCallRequest(request, headers.getRequestHeaders(), service);
         }
      }

      McpSchema.JSONRPCResponse response = null;
      if (result != null) {
         response = new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), result, null);
      } else {
         response = new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), null,
               new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
                     "Unsupported method: " + request.method(), null));
      }

      return response;
   }

   /** Internal record to hold a SSE transport channel with its emitter. */
   record SseTransportChannel(String sessionId, MultiEmitter<? super OutboundSseEvent> emitter, Sse sse) {
   }

   /** Handle the MCP initialize request. */
   private Object handleInitializeRequest(McpSchema.JSONRPCRequest request, Service service) {
      McpSchema.InitializeRequest initializeRequest = mapper.convertValue(request.params(),
            new TypeReference<McpSchema.InitializeRequest>() {
            });

      if (McpSchema.SUPPORTED_PROTOCOL_VERSIONS.contains(initializeRequest.protocolVersion())) {
         McpSchema.ClientCapabilities clientCapabilities = initializeRequest.capabilities();
         McpSchema.Implementation clientInfo = initializeRequest.clientInfo();

         McpSchema.ServerCapabilities serverCapabilities = new McpSchema.ServerCapabilities(null, null,
               new McpSchema.ServerCapabilities.PromptCapabilities(false),
               new McpSchema.ServerCapabilities.ResourceCapabilities(false, false),
               new McpSchema.ServerCapabilities.ToolCapabilities(false));

         return new McpSchema.InitializeResult(initializeRequest.protocolVersion(), serverCapabilities,
               new McpSchema.Implementation(service.getName() + " MCP server", service.getVersion()), null);
      }
      return new McpError("Unsupported protocol version: " + initializeRequest.protocolVersion());
   }

   /** Handle the MCP tools/list request. */
   private Object handleToolsListRequest(McpSchema.JSONRPCRequest request, Service service) {
      // Find the contract resource to build the correct converter.
      Resource resource = getContractResource(service);
      McpToolConverter converter = buildMcpToolConverter(service, resource);

      List<McpSchema.Tool> tools = service.getOperations().stream()
            .map(operation -> new McpSchema.Tool(converter.getToolName(operation),
                  converter.getToolDescription(operation), converter.getInputSchema(operation)))
            .toList();

      return new McpSchema.ListToolsResult(tools, null);
   }

   /** Handle the MCP tools/call request. */
   private Object handleToolsCallRequest(McpSchema.JSONRPCRequest request, Map<String, List<String>> headers,
         Service service) {
      McpSchema.CallToolRequest toolRequest = mapper.convertValue(request.params(),
            new TypeReference<McpSchema.CallToolRequest>() {
            });

      // Find the contract resource to build the correct converter.
      Resource resource = getContractResource(service);
      McpToolConverter converter = buildMcpToolConverter(service, resource);

      Operation callOperation = service.getOperations().stream()
            .filter(operation -> toolRequest.name().equals(converter.getToolName(operation))).findFirst().orElse(null);
      if (callOperation == null) {
         return new McpError("Unknown tool name: " + toolRequest.name());
      }

      Response response = converter.getCallResponse(callOperation, toolRequest, headers);

      return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(response.getContent())),
            response.isFault());
   }

   /** Send a JSONRCPResponse as an SSE event. */
   private void sendSseMessage(SseTransportChannel channel, McpSchema.JSONRPCResponse response) {
      try {
         String jsonText = mapper.writeValueAsString(response);
         channel.emitter()
               .emit(channel.sse().newEventBuilder().name("message").id(channel.sessionId()).data(jsonText).build());
      } catch (Exception e) {
         log.error("Failed to send message to session {}: {}", channel.sessionId(), e.getMessage());
         channel.emitter().fail(e);
      }
   }


   private Resource getContractResource(Service service) {
      List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(), getResourceType(service));
      if (!resources.isEmpty()) {
         return resources.getFirst();
      }
      return null;
   }

   private ResourceType getResourceType(Service service) {
      ResourceType resourceType = ResourceType.OPEN_API_SPEC;
      switch (service.getType()) {
         case GRPC -> resourceType = ResourceType.PROTOBUF_DESCRIPTOR;
         case GRAPHQL -> resourceType = ResourceType.GRAPHQL_SCHEMA;
      }
      return resourceType;
   }

   private McpToolConverter buildMcpToolConverter(Service service, Resource resource) {
      McpToolConverter converter = null;

      switch (service.getType()) {
         case GRPC -> converter = new GrpcMcpToolConverter(service, resource, grpcInvocationProcessor, mapper);
         case GRAPHQL -> converter = new GraphQLMcpToolConverter(service, resource, graphQLInvocationProcessor, mapper);
         default -> converter = new OpenAPIMcpToolConverter(service, resource, resourceRepository,
               restInvocationProcessor, mapper);
      }
      return converter;
   }

   private Header getHeader(String name, Set<Header> headers) {
      if (headers != null) {
         return headers.stream().filter(header -> header.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
      }
      return null;
   }
}
