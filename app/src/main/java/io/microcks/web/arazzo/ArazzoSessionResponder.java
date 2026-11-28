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

import io.microcks.domain.Response;
import io.microcks.service.arazzo.ArazzoSessionAnswerService;
import io.microcks.web.MockControllerCommons;
import io.microcks.web.MockInvocationContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides the response of the REST mocks to an invocation naming an Arazzo verification session: the session answers
 * the step of its workflow the invocation matches. An invocation naming no session keeps the response the mocks
 * dispatched, as it does outside a session.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoSessionResponder {

   private final ArazzoSessionAnswerService arazzoSessionAnswerService;

   /**
    * Build a new ArazzoSessionResponder with its dependencies.
    * @param arazzoSessionAnswerService to decide the answer of a session
    */
   @Inject
   public ArazzoSessionResponder(ArazzoSessionAnswerService arazzoSessionAnswerService) {
      this.arazzoSessionAnswerService = arazzoSessionAnswerService;
   }

   /**
    * @param ic         The service, operation and resource path the invocation was dispatched to
    * @param request    The request of the invocation
    * @param body       The body of the request; null when it has none
    * @param dispatched The response the mocks dispatched; null when they found none
    * @return The response the mocks answer with: the answer of the session the invocation names, else the dispatched
    *         one
    */
   public Response respond(MockInvocationContext ic, HttpServletRequest request, String body, Response dispatched) {
      return MockControllerCommons.getArazzoSessionId(request).flatMap(sessionId -> arazzoSessionAnswerService
            .answer(sessionId, ArazzoSessionRecorder.receivedCall(ic, request, body), dispatched)).orElse(dispatched);
   }
}
