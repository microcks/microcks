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

import io.microcks.domain.GenericResource;
import io.microcks.domain.Operation;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.event.MockInvocationEvent;
import io.microcks.repository.GenericResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.util.SafeLogger;
import io.microcks.util.el.EvaluableRequest;
import io.microcks.util.el.TemplateEngine;
import io.microcks.util.el.TemplateEngineFactory;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.bson.json.JsonParseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.microcks.util.delay.DelayApplierOptions;
import io.microcks.util.delay.DelaySpec;
import io.microcks.util.tracing.CommonAttributes;
import io.microcks.util.tracing.CommonEvents;
import io.microcks.util.tracing.TraceUtil;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

/**
 * This is the controller for Dynamic mocks in Microcks.
 * 
 * @author laurent
 */
@Path("/dynarest")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class DynamicMockRestController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(DynamicMockRestController.class);

   public static final String ID_FIELD = "id";

   private final ServiceRepository serviceRepository;
   private final GenericResourceRepository genericResourceRepository;
   private final Event<MockInvocationEvent> mockInvocationEvents;

   @ConfigProperty(name = "mocks.enable-invocation-stats", defaultValue = "true")
   Boolean enableInvocationStats;

   /** The request parameters and context common to all dynamic mock endpoints. */
   public static class DynamicParams {
      @PathParam("service")
      String service;
      @PathParam("version")
      String version;
      @PathParam("resource")
      String resource;
      @PathParam("resourceId")
      String resourceId;
      @QueryParam("delay")
      Long delay;
      @QueryParam("delayStrategy")
      String delayStrategy;
      @QueryParam("page")
      @DefaultValue("0")
      int page;
      @QueryParam("size")
      @DefaultValue("20")
      int size;
      @Context
      UriInfo uriInfo;
      @Context
      HttpHeaders headers;
      @Context
      HttpServerRequest httpRequest;

      ContainerHttpServletRequest toRequest(String method) {
         return ContainerHttpServletRequest.from(method, uriInfo, headers, httpRequest);
      }
   }

   /**
    * Build a new DynamicMockRestController with required dependencies.
    * 
    * @param serviceRepository         the repository for services
    * @param genericResourceRepository the repository for generic resources
    * @param mockInvocationEvents      the invocation events publisher
    */
   @Inject
   public DynamicMockRestController(ServiceRepository serviceRepository,
         GenericResourceRepository genericResourceRepository, Event<MockInvocationEvent> mockInvocationEvents) {
      this.serviceRepository = serviceRepository;
      this.genericResourceRepository = genericResourceRepository;
      this.mockInvocationEvents = mockInvocationEvents;
   }

   @POST
   @Path("/{service}/{version}/{resource}")
   public Response createResource(@BeanParam DynamicParams p, String body) {
      String serviceName = p.service;
      String version = p.version;
      String resource = p.resource;
      Long requestedDelay = p.delay;
      String requestedDelayStrategy = p.delayStrategy;
      ContainerHttpServletRequest request = p.toRequest("POST");
      log.debug("Creating a new resource '{}' for service '{}-{}'", resource, serviceName, version);
      long startTime = System.currentTimeMillis();

      serviceName = sanitizeServiceName(serviceName);

      // Setup span attributes and event for invocation.
      Span span = Span.current();
      TraceUtil.enableExplainTracing();
      span.setAttribute(CommonAttributes.SERVICE_NAME, serviceName);
      span.setAttribute(CommonAttributes.SERVICE_VERSION, version);
      span.setAttribute(CommonAttributes.OPERATION_NAME, "POST /" + resource);

      // Add an event for the invocation reception with a human-friendly message.
      span.addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(),
            TraceUtil.explainSpanEventBuilder(String.format("Received dynamic mock invocation for POST /%s", resource))
                  .put(CommonAttributes.BODY_SIZE, body != null ? body.length() : 0)
                  .put(CommonAttributes.BODY_CONTENT,
                        body != null ? (body.length() > 1000 ? body.substring(0, 1000) + "..." : body) : "empty")
                  .put(CommonAttributes.URI_FULL, request.getRequestURL().toString())
                  .put(CommonAttributes.CLIENT_ADDRESS, request.getRemoteAddr()).build());

      MockContext mockContext = getMockContext(serviceName, version, "POST /" + resource);
      if (mockContext != null) {
         Document document = null;
         GenericResource genericResource = null;

         try {
            // Try parsing body payload that should be json.
            document = Document.parse(body);
            // Now create a generic resource.
            genericResource = new GenericResource();
            genericResource.setServiceId(mockContext.service.getId());
            genericResource.setPayload(document);

            genericResourceRepository.persistOrUpdate(genericResource);

            span.addEvent("resource.created", TraceUtil.explainSpanEventBuilder("Generic resource created successfully")
                  .put("resource.id", genericResource.getId()).build());
         } catch (JsonParseException jpe) {
            span.addEvent("resource.creation.failed",
                  TraceUtil.explainSpanEventBuilder("Failed to parse JSON payload").build());
            span.setStatus(StatusCode.ERROR, "JSON parsing failed");
            // Return a 422 code : unprocessable entity.
            return Response.status(422).build();
         }

         // Append id and wait if specified before returning.
         document.append(ID_FIELD, genericResource.getId());
         DelaySpec delay = null;
         if (requestedDelay != null) {
            delay = new DelaySpec(requestedDelay, requestedDelayStrategy);
         }

         // Add delay event if delay is configured.
         if (delay != null || mockContext.operation.getDefaultDelay() != null) {
            Long delayValue = delay != null ? delay.baseValue() : mockContext.operation.getDefaultDelay();
            String delayStrategy = delay != null ? delay.strategyName() : DelayApplierOptions.FIXED;
            span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Configured response delay")
                        .put(CommonAttributes.DELAY_VALUE, delayValue != null ? delayValue : 0)
                        .put(CommonAttributes.DELAY_STRATEGY, delayStrategy != null ? delayStrategy : "N/A").build());
         }

         waitForDelay(startTime, delay, mockContext);
         return Response.status(Response.Status.CREATED).entity(document.toJson()).build();
      }
      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   @GET
   @Path("/{service}/{version}/{resource}")
   public Response findResources(@BeanParam DynamicParams p, String body) {
      String serviceName = p.service;
      String version = p.version;
      String resource = p.resource;
      int page = p.page;
      int size = p.size;
      Long requestedDelay = p.delay;
      String requestedDelayStrategy = p.delayStrategy;
      ContainerHttpServletRequest request = p.toRequest("GET");
      log.debug("Find resources '{}' for service '{}-{}'", resource, serviceName, version);
      long startTime = System.currentTimeMillis();

      serviceName = sanitizeServiceName(serviceName);

      // Setup span attributes and event for invocation.
      Span span = Span.current();
      TraceUtil.enableExplainTracing();
      span.setAttribute(CommonAttributes.SERVICE_NAME, serviceName);
      span.setAttribute(CommonAttributes.SERVICE_VERSION, version);
      span.setAttribute(CommonAttributes.OPERATION_NAME, "GET /" + resource);

      // Add an event for the invocation reception.
      span.addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(),
            TraceUtil.explainSpanEventBuilder(String.format("Received dynamic mock invocation for GET /%s", resource))
                  .put("query.page", page).put("query.size", size)
                  .put(CommonAttributes.URI_FULL, request.getRequestURL().toString())
                  .put(CommonAttributes.CLIENT_ADDRESS, request.getRemoteAddr()).build());

      // Build the encoded URI fragment to retrieve simple resourcePath.
      String requestURI = request.getRequestURI();
      String serviceAndVersion = "/" + URLEncoder.encode(serviceName, StandardCharsets.UTF_8).replace("+", "%20") + "/"
            + version;
      String resourcePath = requestURI.substring(requestURI.indexOf(serviceAndVersion) + serviceAndVersion.length());

      MockContext mockContext = getMockContext(serviceName, version, "GET /" + resource);
      if (mockContext != null) {

         List<GenericResource> genericResources = null;
         if (body == null || body.isBlank()) {
            genericResources = genericResourceRepository.findByServiceId(mockContext.service.getId(),
                  Page.of(page, size), Sort.empty());
         } else {
            genericResources = genericResourceRepository.findByServiceIdAndJSONQuery(mockContext.service.getId(), body);
         }

         span.addEvent(CommonEvents.RESPONSE_LOOKUP_COMPLETED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Resource lookup completed")
                     .put(CommonAttributes.RESPONSE_FOUND, !genericResources.isEmpty())
                     .put("resources.count", genericResources.size()).build());

         // Prepare templating support with evaluable request and engine.
         EvaluableRequest evaluableRequest = MockControllerCommons.buildEvaluableRequest(body, resourcePath, request);
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

         // Transform and collect resources.
         List<String> resources = genericResources.stream().map(genericResource -> MockControllerCommons
               .renderResponseContent(evaluableRequest, engine, transformToResourceJSON(genericResource)))
               .collect(Collectors.toList());

         DelaySpec delay = null;
         if (requestedDelay != null) {
            delay = new DelaySpec(requestedDelay, requestedDelayStrategy);
         }

         // Add delay event if delay is configured.
         if (delay != null || mockContext.operation.getDefaultDelay() != null) {
            Long delayValue = delay != null ? delay.baseValue() : mockContext.operation.getDefaultDelay();
            String delayStrategy = delay != null ? delay.strategyName() : DelayApplierOptions.FIXED;
            span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Configured response delay")
                        .put(CommonAttributes.DELAY_VALUE, delayValue != null ? delayValue : 0)
                        .put(CommonAttributes.DELAY_STRATEGY, delayStrategy != null ? delayStrategy : "N/A").build());
         }

         // Wait if specified before returning.
         waitForDelay(startTime, delay, mockContext);

         return Response.status(Response.Status.OK).entity(formatToJSONArray(resources)).build();
      }
      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   @GET
   @Path("/{service}/{version}/{resource}/{resourceId}")
   public Response getResource(@BeanParam DynamicParams p) {
      String serviceName = p.service;
      String version = p.version;
      String resource = p.resource;
      String resourceId = p.resourceId;
      Long requestedDelay = p.delay;
      String requestedDelayStrategy = p.delayStrategy;
      ContainerHttpServletRequest request = p.toRequest("GET");
      log.debug("Get resource '{}:{}' for service '{}-{}'", resource, resourceId, serviceName, version);
      long startTime = System.currentTimeMillis();

      serviceName = sanitizeServiceName(serviceName);

      // Setup span attributes and event for invocation.
      Span span = Span.current();
      TraceUtil.enableExplainTracing();
      span.setAttribute(CommonAttributes.SERVICE_NAME, serviceName);
      span.setAttribute(CommonAttributes.SERVICE_VERSION, version);
      span.setAttribute(CommonAttributes.OPERATION_NAME, "GET /" + resource + "/:id");

      // Add an event for the invocation reception.
      span.addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(),
            TraceUtil
                  .explainSpanEventBuilder(String.format("Received dynamic mock invocation for GET /%s/:id", resource))
                  .put("resource.id", resourceId).put(CommonAttributes.URI_FULL, request.getRequestURL().toString())
                  .put(CommonAttributes.CLIENT_ADDRESS, request.getRemoteAddr()).build());

      // Build the encoded URI fragment to retrieve simple resourcePath.
      String requestURI = request.getRequestURI();
      String serviceAndVersion = "/" + URLEncoder.encode(serviceName, StandardCharsets.UTF_8).replace("+", "%20") + "/"
            + version;
      String resourcePath = requestURI.substring(requestURI.indexOf(serviceAndVersion) + serviceAndVersion.length());

      MockContext mockContext = getMockContext(serviceName, version, "GET /" + resource + "/:id");
      if (mockContext != null) {
         // Get the requested generic resource.
         GenericResource genericResource = genericResourceRepository.findById(resourceId);

         span.addEvent(CommonEvents.RESPONSE_LOOKUP_COMPLETED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Resource lookup completed")
                     .put(CommonAttributes.RESPONSE_FOUND, genericResource != null).put("resource.id", resourceId)
                     .build());

         DelaySpec delay = null;
         if (requestedDelay != null) {
            delay = new DelaySpec(requestedDelay, requestedDelayStrategy);
         }

         // Add delay event if delay is configured.
         if (delay != null || mockContext.operation.getDefaultDelay() != null) {
            Long delayValue = delay != null ? delay.baseValue() : mockContext.operation.getDefaultDelay();
            String delayStrategy = delay != null ? delay.strategyName() : DelayApplierOptions.FIXED;
            span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Configured response delay")
                        .put(CommonAttributes.DELAY_VALUE, delayValue != null ? delayValue : 0)
                        .put(CommonAttributes.DELAY_STRATEGY, delayStrategy != null ? delayStrategy : "N/A").build());
         }

         // Wait if specified before returning.
         waitForDelay(startTime, delay, mockContext);

         if (genericResource != null) {
            // Prepare templating support with evaluable request and engine.
            EvaluableRequest evaluableRequest = MockControllerCommons.buildEvaluableRequest(null, resourcePath,
                  request);
            TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

            // Return the resource as well as a 200 code.
            return Response.status(Response.Status.OK).entity(MockControllerCommons
                  .renderResponseContent(evaluableRequest, engine, transformToResourceJSON(genericResource))).build();
         } else {
            span.addEvent(CommonEvents.NO_RESPONSE_FOUND.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Resource not found").put("resource.id", resourceId)
                        .put(CommonAttributes.ERROR_STATUS, 404).build());
            // Return a 404 code : not found.
            return Response.status(Response.Status.NOT_FOUND).build();
         }
      }

      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   @PUT
   @Path("/{service}/{version}/{resource}/{resourceId}")
   public Response updateResource(@BeanParam DynamicParams p, String body) {
      String serviceName = p.service;
      String version = p.version;
      String resource = p.resource;
      String resourceId = p.resourceId;
      Long requestedDelay = p.delay;
      String requestedDelayStrategy = p.delayStrategy;
      ContainerHttpServletRequest request = p.toRequest("PUT");
      log.debug("Update resource '{}:{}' for service '{}-{}'", resource, resourceId, serviceName, version);
      long startTime = System.currentTimeMillis();

      serviceName = sanitizeServiceName(serviceName);

      // Setup span attributes and event for invocation.
      Span span = Span.current();
      TraceUtil.enableExplainTracing();
      span.setAttribute(CommonAttributes.SERVICE_NAME, serviceName);
      span.setAttribute(CommonAttributes.SERVICE_VERSION, version);
      span.setAttribute(CommonAttributes.OPERATION_NAME, "PUT /" + resource + "/:id");

      // Add an event for the invocation reception.
      span.addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(),
            TraceUtil
                  .explainSpanEventBuilder(String.format("Received dynamic mock invocation for PUT /%s/:id", resource))
                  .put("resource.id", resourceId).put(CommonAttributes.BODY_SIZE, body != null ? body.length() : 0)
                  .put(CommonAttributes.URI_FULL, request.getRequestURL().toString())
                  .put(CommonAttributes.CLIENT_ADDRESS, request.getRemoteAddr()).build());

      DelaySpec delay = null;
      if (requestedDelay != null) {
         delay = new DelaySpec(requestedDelay, requestedDelayStrategy);
      }

      MockContext mockContext = getMockContext(serviceName, version, "PUT /" + resource + "/:id");
      if (mockContext != null) {
         // Get the requested generic resource.
         GenericResource genericResource = genericResourceRepository.findById(resourceId);

         span.addEvent(CommonEvents.RESPONSE_LOOKUP_COMPLETED.getEventName(),
               TraceUtil.explainSpanEventBuilder("Resource lookup completed")
                     .put(CommonAttributes.RESPONSE_FOUND, genericResource != null).put("resource.id", resourceId)
                     .build());

         if (genericResource != null) {
            Document document = null;

            try {
               // Try parsing body payload that should be json.
               document = Document.parse(body);
               document.remove(ID_FIELD);

               // Now update the generic resource payload.
               genericResource.setPayload(document);

               genericResourceRepository.persistOrUpdate(genericResource);

               span.addEvent("resource.updated",
                     TraceUtil.explainSpanEventBuilder("Generic resource updated successfully")
                           .put("resource.id", genericResource.getId()).build());
            } catch (JsonParseException jpe) {
               span.addEvent("resource.update.failed",
                     TraceUtil.explainSpanEventBuilder("Failed to parse JSON payload").build());
               span.setStatus(StatusCode.ERROR, "JSON parsing failed");
               // Return a 422 code : unprocessable entity.
               return Response.status(422).build();
            }

            // Add delay event if delay is configured.
            if (delay != null || mockContext.operation.getDefaultDelay() != null) {
               Long delayValue = delay != null ? delay.baseValue() : mockContext.operation.getDefaultDelay();
               String delayStrategy = delay != null ? delay.strategyName() : DelayApplierOptions.FIXED;
               span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
                     TraceUtil.explainSpanEventBuilder("Configured response delay")
                           .put(CommonAttributes.DELAY_VALUE, delayValue != null ? delayValue : 0)
                           .put(CommonAttributes.DELAY_STRATEGY, delayStrategy != null ? delayStrategy : "N/A")
                           .build());
            }

            // Wait if specified before returning.
            waitForDelay(startTime, delay, mockContext);

            // Return the updated resource as well as a 200 code.
            return Response.status(Response.Status.OK).entity(transformToResourceJSON(genericResource)).build();

         } else {
            span.addEvent(CommonEvents.NO_RESPONSE_FOUND.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Resource not found for update").put("resource.id", resourceId)
                        .put(CommonAttributes.ERROR_STATUS, 404).build());

            // Add delay event if delay is configured.
            if (delay != null || mockContext.operation.getDefaultDelay() != null) {
               Long delayValue = delay != null ? delay.baseValue() : mockContext.operation.getDefaultDelay();
               String delayStrategy = delay != null ? delay.strategyName() : DelayApplierOptions.FIXED;
               span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
                     TraceUtil.explainSpanEventBuilder("Configured response delay")
                           .put(CommonAttributes.DELAY_VALUE, delayValue != null ? delayValue : 0)
                           .put(CommonAttributes.DELAY_STRATEGY, delayStrategy != null ? delayStrategy : "N/A")
                           .build());
            }

            // Wait if specified before returning.
            waitForDelay(startTime, delay, mockContext);

            // Return a 404 code : not found.
            return Response.status(Response.Status.NOT_FOUND).build();
         }
      }

      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   @DELETE
   @Path("/{service}/{version}/{resource}/{resourceId}")
   public Response deleteResource(@BeanParam DynamicParams p) {
      String serviceName = p.service;
      String version = p.version;
      String resource = p.resource;
      String resourceId = p.resourceId;
      Long requestedDelay = p.delay;
      String requestedDelayStrategy = p.delayStrategy;
      ContainerHttpServletRequest request = p.toRequest("DELETE");
      log.debug("Update resource '{}:{}' for service '{}-{}'", resource, resourceId, serviceName, version);
      long startTime = System.currentTimeMillis();

      serviceName = sanitizeServiceName(serviceName);

      // Setup span attributes and event for invocation.
      Span span = Span.current();
      TraceUtil.enableExplainTracing();
      span.setAttribute(CommonAttributes.SERVICE_NAME, serviceName);
      span.setAttribute(CommonAttributes.SERVICE_VERSION, version);
      span.setAttribute(CommonAttributes.OPERATION_NAME, "DELETE /" + resource + "/:id");

      // Add an event for the invocation reception.
      span.addEvent(CommonEvents.INVOCATION_RECEIVED.getEventName(), TraceUtil
            .explainSpanEventBuilder(String.format("Received dynamic mock invocation for DELETE /%s/:id", resource))
            .put("resource.id", resourceId).put(CommonAttributes.URI_FULL, request.getRequestURL().toString())
            .put(CommonAttributes.CLIENT_ADDRESS, request.getRemoteAddr()).build());

      DelaySpec delay = null;
      if (requestedDelay != null) {
         delay = new DelaySpec(requestedDelay, requestedDelayStrategy);
      }

      MockContext mockContext = getMockContext(serviceName, version, "DELETE /" + resource + "/:id");
      if (mockContext != null) {
         genericResourceRepository.deleteById(resourceId);

         span.addEvent("resource.deleted", TraceUtil.explainSpanEventBuilder("Generic resource deleted successfully")
               .put("resource.id", resourceId).build());

         // Add delay event if delay is configured.
         if (delay != null || mockContext.operation.getDefaultDelay() != null) {
            Long delayValue = delay != null ? delay.baseValue() : mockContext.operation.getDefaultDelay();
            String delayStrategy = delay != null ? delay.strategyName() : DelayApplierOptions.FIXED;
            span.addEvent(CommonEvents.DELAY_CONFIGURED.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Configured response delay")
                        .put(CommonAttributes.DELAY_VALUE, delayValue != null ? delayValue : 0)
                        .put(CommonAttributes.DELAY_STRATEGY, delayStrategy != null ? delayStrategy : "N/A").build());
         }

         // Wait if specified before returning.
         waitForDelay(startTime, delay, mockContext);

         // Return a 204 code : done and no content returned.
         return Response.status(Response.Status.NO_CONTENT).build();
      }

      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   /** Sanitize the service name (check encoding and so on...) */
   private String sanitizeServiceName(String serviceName) {
      // If serviceName was encoded with '+' instead of '%20', replace them.
      if (serviceName.contains("+")) {
         return serviceName.replace('+', ' ');
      }
      return serviceName;
   }

   /**
    * Retrieve a MockContext corresponding to operation on service. Null if not found or not valid.
    */
   private MockContext getMockContext(String serviceName, String version, String operationName) {
      Service service = serviceRepository.findByNameAndVersion(serviceName, version);
      if (service != null && ServiceType.GENERIC_REST.equals(service.getType())) {
         for (Operation operation : service.getOperations()) {
            if (operationName.equals(operation.getName())) {
               return new MockContext(service, operation);
            }
         }
      }
      return null;
   }

   private String transformToResourceJSON(GenericResource genericResource) {
      Document document = genericResource.getPayload();
      document.append(ID_FIELD, genericResource.getId());
      return document.toJson();
   }

   private String formatToJSONArray(List<String> resources) {
      StringBuilder builder = new StringBuilder("[");
      for (int i = 0; i < resources.size(); i++) {
         builder.append(resources.get(i));
         if (i < resources.size() - 1) {
            builder.append(", ");
         }
      }
      return builder.append("]").toString();
   }

   private void waitForDelay(Long since, DelaySpec delay, MockContext mockContext) {
      // Setting delay to default one if not set.
      if (delay == null && mockContext.operation.getDefaultDelay() != null) {
         Long operationDelay = mockContext.operation.getDefaultDelay();
         String delayStrategy = mockContext.operation.getDefaultDelayStrategy();
         if (delayStrategy == null) {
            delayStrategy = DelayApplierOptions.FIXED;
         }
         delay = new DelaySpec(operationDelay, delayStrategy);
      }

      MockControllerCommons.waitForDelay(since, delay);

      // Publish an invocation event before returning if enabled.
      if (Boolean.TRUE.equals(enableInvocationStats)) {
         MockInvocationEvent event = new MockInvocationEvent(mockContext.service.getName(),
               mockContext.service.getVersion(), "DynamicMockRestController", new Date(since),
               since - System.currentTimeMillis());
         mockInvocationEvents.fireAsync(event);
         log.debug("Mock invocation event has been published");
      }
   }

   private class MockContext {
      public Service service;
      public Operation operation;

      public MockContext(Service service, Operation operation) {
         this.service = service;
         this.operation = operation;
      }
   }
}
