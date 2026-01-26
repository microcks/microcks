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
package io.github.microcks.web;

import io.github.microcks.domain.GenericResource;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.event.MockInvocationEvent;
import io.github.microcks.repository.GenericResourceRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import org.bson.Document;
import org.bson.json.JsonParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.github.microcks.util.delay.DelayApplierOptions;
import io.github.microcks.util.delay.DelaySpec;
import io.github.microcks.util.tracing.CommonAttributes;
import io.github.microcks.util.tracing.CommonEvents;
import io.github.microcks.util.tracing.TraceUtil;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

/**
 * This is the controller for Dynamic mocks in Microcks.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/dynarest")
public class DynamicMockRestController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(DynamicMockRestController.class);

   public static final String ID_FIELD = "id";

   private final ServiceRepository serviceRepository;
   private final GenericResourceRepository genericResourceRepository;
   private final ApplicationContext applicationContext;

   @Value("${mocks.enable-invocation-stats:false}")
   private Boolean enableInvocationStats;

   /**
    * Build a new DynamicMockRestController with required dependencies.
    * @param serviceRepository         the repository for services
    * @param genericResourceRepository the repository for generic resources
    * @param applicationContext        the Spring application context
    */
   public DynamicMockRestController(ServiceRepository serviceRepository,
         GenericResourceRepository genericResourceRepository, ApplicationContext applicationContext) {
      this.serviceRepository = serviceRepository;
      this.genericResourceRepository = genericResourceRepository;
      this.applicationContext = applicationContext;
   }

   @PostMapping(value = "/{service}/{version}/{resource}", produces = "application/json")
   public ResponseEntity<String> createResource(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @PathVariable("resource") String resource,
         @RequestParam(value = "delay", required = false) Long requestedDelay,
         @RequestParam(value = "delayStrategy", required = false) String requestedDelayStrategy,
         @RequestBody(required = true) String body, HttpServletRequest request) {
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

            genericResource = genericResourceRepository.save(genericResource);

            span.addEvent("resource.created", TraceUtil.explainSpanEventBuilder("Generic resource created successfully")
                  .put("resource.id", genericResource.getId()).build());
         } catch (JsonParseException jpe) {
            span.addEvent("resource.creation.failed",
                  TraceUtil.explainSpanEventBuilder("Failed to parse JSON payload").build());
            span.setStatus(StatusCode.ERROR, "JSON parsing failed");
            // Return a 422 code : unprocessable entity.
            return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);
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
         return new ResponseEntity<>(document.toJson(), HttpStatus.CREATED);
      }
      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }


   @GetMapping(value = "/{service}/{version}/{resource}", produces = "application/json")
   public ResponseEntity<String> findResources(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @PathVariable("resource") String resource,
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size,
         @RequestParam(value = "delay", required = false) Long requestedDelay,
         @RequestParam(value = "delayStrategy", required = false) String requestedDelayStrategy,
         @RequestBody(required = false) String body, HttpServletRequest request) {
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
      String serviceAndVersion = "/" + UriUtils.encodeFragment(serviceName, "UTF-8") + "/" + version;
      String resourcePath = requestURI.substring(requestURI.indexOf(serviceAndVersion) + serviceAndVersion.length());

      MockContext mockContext = getMockContext(serviceName, version, "GET /" + resource);
      if (mockContext != null) {

         List<GenericResource> genericResources = null;
         if (body == null) {
            genericResources = genericResourceRepository.findByServiceId(mockContext.service.getId(),
                  PageRequest.of(page, size));
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

         return new ResponseEntity<>(formatToJSONArray(resources), HttpStatus.OK);
      }
      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }


   @GetMapping(value = "/{service}/{version}/{resource}/{resourceId}", produces = "application/json")
   public ResponseEntity<String> getResource(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @PathVariable("resource") String resource,
         @PathVariable("resourceId") String resourceId,
         @RequestParam(value = "delay", required = false) Long requestedDelay,
         @RequestParam(value = "delayStrategy", required = false) String requestedDelayStrategy,
         HttpServletRequest request) {
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
      String serviceAndVersion = "/" + UriUtils.encodeFragment(serviceName, "UTF-8") + "/" + version;
      String resourcePath = requestURI.substring(requestURI.indexOf(serviceAndVersion) + serviceAndVersion.length());

      MockContext mockContext = getMockContext(serviceName, version, "GET /" + resource + "/:id");
      if (mockContext != null) {
         // Get the requested generic resource.
         GenericResource genericResource = genericResourceRepository.findById(resourceId).orElse(null);

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
            return new ResponseEntity<>(MockControllerCommons.renderResponseContent(evaluableRequest, engine,
                  transformToResourceJSON(genericResource)), HttpStatus.OK);
         } else {
            span.addEvent(CommonEvents.NO_RESPONSE_FOUND.getEventName(),
                  TraceUtil.explainSpanEventBuilder("Resource not found").put("resource.id", resourceId)
                        .put(CommonAttributes.ERROR_STATUS, 404).build());
            // Return a 404 code : not found.
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
         }
      }

      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }


   @PutMapping(value = "/{service}/{version}/{resource}/{resourceId}", produces = "application/json")
   public ResponseEntity<String> updateResource(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @PathVariable("resource") String resource,
         @PathVariable("resourceId") String resourceId,
         @RequestParam(value = "delay", required = false) Long requestedDelay,
         @RequestParam(value = "delayStrategy", required = false) String requestedDelayStrategy,
         @RequestBody(required = true) String body, HttpServletRequest request) {
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
         GenericResource genericResource = genericResourceRepository.findById(resourceId).orElse(null);

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

               genericResourceRepository.save(genericResource);

               span.addEvent("resource.updated",
                     TraceUtil.explainSpanEventBuilder("Generic resource updated successfully")
                           .put("resource.id", genericResource.getId()).build());
            } catch (JsonParseException jpe) {
               span.addEvent("resource.update.failed",
                     TraceUtil.explainSpanEventBuilder("Failed to parse JSON payload").build());
               span.setStatus(StatusCode.ERROR, "JSON parsing failed");
               // Return a 422 code : unprocessable entity.
               return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);
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
            return new ResponseEntity<>(transformToResourceJSON(genericResource), HttpStatus.OK);

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
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
         }
      }

      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }


   @DeleteMapping(value = "/{service}/{version}/{resource}/{resourceId}")
   public ResponseEntity<String> deleteResource(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @PathVariable("resource") String resource,
         @PathVariable("resourceId") String resourceId,
         @RequestParam(value = "delay", required = false) Long requestedDelay,
         @RequestParam(value = "delayStrategy", required = false) String requestedDelayStrategy,
         HttpServletRequest request) {
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
         return new ResponseEntity<>(HttpStatus.NO_CONTENT);
      }

      span.setStatus(StatusCode.ERROR, "Service or operation not found");
      // Return a 400 code : bad request.
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }


   /** Sanitize the service name (check encoding and so on...) */
   private String sanitizeServiceName(String serviceName) {
      // If serviceName was encoded with '+' instead of '%20', replace them.
      if (serviceName.contains("+")) {
         return serviceName.replace('+', ' ');
      }
      return serviceName;
   }

   /** Retrieve a MockContext corresponding to operation on service. Null if not found or not valid. */
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
         // TODO: Get DelayStrategy
         delay = new DelaySpec(operationDelay, DelayApplierOptions.FIXED);
      }

      MockControllerCommons.waitForDelay(since, delay);

      // Publish an invocation event before returning if enabled.
      if (Boolean.TRUE.equals(enableInvocationStats)) {
         MockInvocationEvent event = new MockInvocationEvent(this, mockContext.service.getName(),
               mockContext.service.getVersion(), "DynamicMockRestController", new Date(since),
               since - System.currentTimeMillis());
         applicationContext.publishEvent(event);
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
