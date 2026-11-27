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
package io.microcks.web.arazzo;

import io.microcks.artifact.grpc.GrpcMetadataUtil;
import io.microcks.domain.Header;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.service.arazzo.ArazzoSessionService;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.tracing.ArazzoSessionCarrier;
import io.microcks.util.tracing.ArazzoTraceAttributes;
import io.microcks.web.GrpcResponseResult;
import io.microcks.web.MockControllerCommons;
import io.microcks.web.MockInvocationContext;
import io.microcks.web.ResponseResult;

import io.grpc.Metadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Records the REST and gRPC mock invocations naming a verification session in that session, as the mocks received and
 * answered them. An invocation naming no session is left as it is.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoSessionRecorder {

   /** The method a gRPC call is recorded with, as it has no HTTP one. */
   static final String GRPC = "GRPC";

   private static final Metadata.Key<String> SESSION_KEY = Metadata.Key.of(ArazzoSessionCarrier.SESSION_HEADER,
         Metadata.ASCII_STRING_MARSHALLER);
   private static final Metadata.Key<String> BAGGAGE_KEY = Metadata.Key.of(ArazzoTraceAttributes.BAGGAGE_HEADER,
         Metadata.ASCII_STRING_MARSHALLER);

   private final ArazzoSessionService arazzoSessionService;

   /**
    * Build a new ArazzoSessionRecorder with its dependencies.
    * @param arazzoSessionService to record the calls in their session
    */
   @Inject
   public ArazzoSessionRecorder(ArazzoSessionService arazzoSessionService) {
      this.arazzoSessionService = arazzoSessionService;
   }

   /**
    * Record a REST mock invocation in the session it names, if any.
    * @param ic       The service, operation and resource path the invocation was dispatched to
    * @param request  The request of the invocation
    * @param body     The body of the request; null when it has none
    * @param response The response of the mocks
    */
   public void recordRest(MockInvocationContext ic, HttpServletRequest request, String body, ResponseResult response) {
      MockControllerCommons.getArazzoSessionId(request).ifPresent(sessionId -> {
         ArazzoSessionCall call = receivedCall(ic, request, body);
         call.setResponseStatus(response.status().value());
         call.setResponseHeaders(headersOf(response.headers()));
         call.setResponseBody(
               response.content() == null ? null : new String(response.content(), StandardCharsets.UTF_8));
         arazzoSessionService.recordCall(sessionId, call);
      });
   }

   /**
    * A REST mock invocation, as the mocks received it, before they answer it.
    * @param ic      The service, operation and resource path the invocation was dispatched to
    * @param request The request of the invocation
    * @param body    The body of the request; null when it has none
    * @return The call, without response
    */
   static ArazzoSessionCall receivedCall(MockInvocationContext ic, HttpServletRequest request, String body) {
      ArazzoSessionCall call = new ArazzoSessionCall();
      call.setServiceName(ic.service().getName());
      call.setServiceVersion(ic.service().getVersion());
      call.setOperationName(ic.operation().getName());
      call.setMethod(request.getMethod());
      call.setPath(ic.resourcePath());
      call.setRequestUrl(
            request.getRequestURL() + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
      call.setQueryString(request.getQueryString());
      call.setRequestHeaders(headersOf(request));
      call.setRequestBody(body);
      return call;
   }

   /**
    * Record a gRPC mock invocation in the session its metadata names, if any.
    * @param ic       The service and operation the invocation was dispatched to
    * @param metadata The metadata of the invocation; null when it has none
    * @param body     The request message, as JSON
    * @param response The response of the mocks
    */
   public void recordGrpc(MockInvocationContext ic, Metadata metadata, String body, GrpcResponseResult response) {
      if (metadata == null) {
         return;
      }
      ArazzoSessionCarrier.sessionId(metadata.get(SESSION_KEY), listOf(metadata.getAll(BAGGAGE_KEY)))
            .ifPresent(sessionId -> {
               ArazzoSessionCall call = new ArazzoSessionCall();
               call.setServiceName(ic.service().getName());
               call.setServiceVersion(ic.service().getVersion());
               call.setOperationName(ic.operation().getName());
               call.setMethod(GRPC);
               call.setPath(ic.operation().getName());
               call.setRequestHeaders(headersOf(metadata));
               call.setRequestBody(body);
               call.setResponseStatus(response.status().getCode().value());
               call.setResponseBody(response.isError() ? response.errorDescription() : response.content());
               arazzoSessionService.recordCall(sessionId, call);
            });
   }

   /** The text entries of gRPC metadata, but the remote address Microcks adds itself. */
   private static Set<Header> headersOf(Metadata metadata) {
      Set<Header> headers = new LinkedHashSet<>();
      for (String key : metadata.keys()) {
         if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)
               && !key.equals(GrpcMetadataUtil.REMOTE_ADDR_METADATA_KEY.name())) {
            headers.add(new Header(key,
                  new HashSet<>(listOf(metadata.getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))))));
         }
      }
      return headers;
   }

   private static List<String> listOf(Iterable<String> values) {
      List<String> list = new ArrayList<>();
      if (values != null) {
         values.forEach(list::add);
      }
      return list;
   }

   private static Set<Header> headersOf(HttpServletRequest request) {
      Set<Header> headers = new LinkedHashSet<>();
      for (String name : Collections.list(request.getHeaderNames())) {
         headers.add(new Header(name, new HashSet<>(Collections.list(request.getHeaders(name)))));
      }
      return headers;
   }

   private static Set<Header> headersOf(HttpHeaders httpHeaders) {
      Set<Header> headers = new LinkedHashSet<>();
      if (httpHeaders != null) {
         httpHeaders.headerSet()
               .forEach(entry -> headers.add(new Header(entry.getKey(), new HashSet<>(entry.getValue()))));
      }
      return headers;
   }
}
