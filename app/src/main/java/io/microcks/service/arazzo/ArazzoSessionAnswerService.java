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
package io.microcks.service.arazzo;

import io.microcks.artifact.arazzo.ArazzoExpressionEvaluator;
import io.microcks.artifact.arazzo.ArazzoExpressionException;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;
import io.microcks.domain.Response;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.domain.arazzo.ArazzoStepCall;
import io.microcks.repository.RequestRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.ServiceStateRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.service.ServiceStateStore;
import io.microcks.service.StateStore;
import io.microcks.service.arazzo.ArazzoDocumentService.ResolvedSource;
import io.microcks.service.arazzo.ArazzoSessionVerifier.ExpectedStep;
import io.microcks.service.arazzo.ArazzoSessionVerifier.ReceivedCall;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service bean deciding how the mocks answer a call made in an open Arazzo verification session: the step of the
 * workflow the call matches answers with the example the mocks dispatched, or the example of its operation when they
 * dispatched none, filled with the values of the session. The values the earlier steps gave are the state of the
 * session: the outputs of each answered step, kept in the state store of the API that answered it, under a key of the
 * session, and expiring with the session.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoSessionAnswerService {

   private static final ObjectMapper JSON = new ObjectMapper();
   private static final ArazzoExpressionEvaluator EXPRESSIONS = new ArazzoExpressionEvaluator();

   private final ArazzoDocumentService arazzoDocumentService;
   private final ArazzoSessionRepository arazzoSessionRepository;
   private final RequestRepository requestRepository;
   private final ResponseRepository responseRepository;
   private final ServiceStateRepository serviceStateRepository;

   /**
    * Build a new ArazzoSessionAnswerService with its required dependencies.
    * @param arazzoDocumentService   The service finding the Arazzo documents and linking their sources to APIs
    * @param arazzoSessionRepository The repository of the sessions
    * @param requestRepository       The repository of the requests of the examples
    * @param responseRepository      The repository of the responses of the examples
    * @param serviceStateRepository  The repository of the state stores of the APIs
    */
   @Inject
   public ArazzoSessionAnswerService(ArazzoDocumentService arazzoDocumentService,
         ArazzoSessionRepository arazzoSessionRepository, RequestRepository requestRepository,
         ResponseRepository responseRepository, ServiceStateRepository serviceStateRepository) {
      this.arazzoDocumentService = arazzoDocumentService;
      this.arazzoSessionRepository = arazzoSessionRepository;
      this.requestRepository = requestRepository;
      this.responseRepository = responseRepository;
      this.serviceStateRepository = serviceStateRepository;
   }

   /**
    * @param sessionId  The id of the session the call names
    * @param received   The call, as the mocks received it
    * @param dispatched The response the mocks dispatched to the call; null when they found none
    * @return The answer of the session to the call; empty when the session is not open, or when the call matches no
    *         step of its workflow or its operation has no example: the mocks then answer as they do outside a session
    */
   public Optional<Response> answer(String sessionId, ArazzoSessionCall received, Response dispatched) {
      Date now = new Date();
      return arazzoSessionRepository.findByIdOptional(sessionId)
            .filter(session -> session.getClosedDate() == null && session.getExpireAt().after(now))
            .flatMap(this::stepsOf).flatMap(
                  steps -> steps.stepOf(received)
                        .flatMap(step -> Optional.ofNullable(dispatched)
                              .or(() -> exampleOf(steps.serviceIdOf(step), received))
                              .map(example -> filled(example, step, received, steps.context(null)))));
   }

   /**
    * The example the step answers with, filled with the values of the session; as it is when Microcks keeps no request
    * with it.
    */
   private Response filled(Response example, ExpectedStep step, ArazzoSessionCall received, ReceivedCall session) {
      Map<String, Object> replacements = requestRepository
            .findByOperationIdAndName(example.getOperationId(), example.getName()).stream().findFirst()
            .map(exampleRequest -> ArazzoSessionAnswerFiller.replacements(step.step(), exampleRequest,
                  ArazzoSessionVerifier.requestOf(step, received), session))
            .orElse(Map.of());
      Response answer = copyOf(example);
      answer.setContent(ArazzoSessionAnswerFiller.filled(example.getContent(), replacements));
      return answer;
   }

   /**
    * Keep the outputs of the step a call recorded in a session answered, read from the response of the mocks, in the
    * state of the session.
    * @param session The session the call was recorded in
    * @param call    The call, as the mocks received and answered it
    */
   public void remember(ArazzoSession session, ArazzoSessionCall call) {
      stepsOf(session).ifPresent(steps -> steps.stepOf(call).ifPresent(step -> {
         Map<String, Object> outputs = new LinkedHashMap<>();
         ReceivedCall answered = steps.context(call);
         step.step().outputs().forEach((name, expression) -> {
            try {
               outputs.put(name, EXPRESSIONS.resolve(expression, answered));
            } catch (ArazzoExpressionException noValue) {
               // The output comes from a response the mocks did not give.
            }
         });
         try {
            steps.store(step).put(stateKey(session.getId(), step), JSON.writeValueAsString(outputs),
                  secondsUntil(session.getExpireAt()));
         } catch (JsonProcessingException jpe) {
            // The outputs read from a JSON response are written back as JSON.
         }
      }));
   }

   /**
    * Let the state of a session expire with the session, once closing it moved its expiry.
    * @param sessionId The id of the session
    */
   public void expireWithSession(String sessionId) {
      arazzoSessionRepository.findByIdOptional(sessionId).ifPresent(session -> stepsOf(session).ifPresent(steps -> {
         for (ExpectedStep step : steps.steps()) {
            String outputs = steps.store(step).get(stateKey(sessionId, step));
            if (outputs != null) {
               steps.store(step).put(stateKey(sessionId, step), outputs, secondsUntil(session.getExpireAt()));
            }
         }
      }));
   }

   /** The steps of the workflow of a session; empty when it names no document, or its document or workflow is gone. */
   private Optional<SessionSteps> stepsOf(ArazzoSession session) {
      return Optional.ofNullable(session.getDocumentId()).flatMap(arazzoDocumentService::getDocument)
            .flatMap(document -> workflowOf(document, session.getWorkflowId()).map(workflow -> {
               Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document);
               List<ExpectedStep> steps = new ArrayList<>();
               for (ArazzoStep step : workflow.steps()) {
                  ResolvedSource source = sources.get(step.call().sourceName());
                  steps.add(new ExpectedStep(steps.size(), step,
                        ArazzoDocumentService.operationOf(new ArazzoStepCall(step.call().sourceName(),
                              step.call().operationId(), step.call().channel()), sources),
                        source.link().apiName(), source.link().apiVersion(), null));
               }
               return new SessionSteps(session.getId(), session.getInputs() == null ? Map.of() : session.getInputs(),
                     sources, steps, serviceStateRepository);
            }));
   }

   private static Optional<ArazzoWorkflow> workflowOf(ArazzoDocument document, String workflowId) {
      return Optional.ofNullable(document.getContent()).map(ArazzoWorkflowService::parse)
            .flatMap(content -> ArazzoWorkflowReader.workflow(content, workflowId));
   }

   /**
    * The first success example of the operation a call reached, else its first example; empty when it has none. The
    * responses of its callbacks are not examples of the operation.
    */
   private Optional<Response> exampleOf(String serviceId, ArazzoSessionCall received) {
      return responseRepository.findByOperationId(serviceId + "-" + received.getOperationName()).stream()
            .filter(response -> response.getCallbackName() == null).min(Comparator.comparing(Response::isFault));
   }

   private static String stateKey(String sessionId, ExpectedStep step) {
      return "arazzo-session:" + sessionId + ":" + step.step().stepId();
   }

   private static int secondsUntil(Date expireAt) {
      return (int) ((expireAt.getTime() - System.currentTimeMillis()) / 1000L);
   }

   private static Response copyOf(Response example) {
      Response copy = new Response();
      copy.setName(example.getName());
      copy.setStatus(example.getStatus());
      copy.setMediaType(example.getMediaType());
      copy.setHeaders(example.getHeaders());
      return copy;
   }

   /**
    * The steps of the workflow of a session.
    * @param sessionId The id of the session
    * @param inputs    The inputs of the workflow the session was opened with, by name
    * @param sources   The source descriptions of the document, with the APIs they are linked to, by name
    * @param steps     The steps, in the order of the workflow
    * @param states    The repository of the state stores of the APIs
    */
   private record SessionSteps(String sessionId, Map<String, Object> inputs, Map<String, ResolvedSource> sources,
         List<ExpectedStep> steps, ServiceStateRepository states) {

      /** The id of the API the source of a step is linked to. */
      private String serviceIdOf(ExpectedStep step) {
         return sources.get(step.step().call().sourceName()).serviceId();
      }

      /** The state store of the API the source of a step is linked to. */
      private StateStore store(ExpectedStep step) {
         return new ServiceStateStore(states, serviceIdOf(step));
      }

      /**
       * The step a call matches: the first step of its operation the session has not answered yet, else the last one,
       * which answers again.
       */
      private Optional<ExpectedStep> stepOf(ArazzoSessionCall call) {
         List<ExpectedStep> candidates = steps.stream().filter(step -> ArazzoSessionVerifier.calls(call, step))
               .toList();
         return candidates.stream().filter(step -> store(step).get(stateKey(sessionId, step)) == null).findFirst()
               .or(() -> candidates.stream().reduce((first, second) -> second));
      }

      /**
       * What the runtime expressions of a step see in the session: its inputs, the outputs of the steps it answered
       * and, once the mocks answered it, the response to the call.
       */
      private ReceivedCall context(ArazzoSessionCall call) {
         return new ReceivedCall(inputs, outputs(), call);
      }

      /** The outputs of the steps the session answered, by step id. */
      private Map<String, Map<String, Object>> outputs() {
         Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
         for (ExpectedStep step : steps) {
            String stored = store(step).get(stateKey(sessionId, step));
            if (stored != null) {
               try {
                  outputs.put(step.step().stepId(), JSON.readValue(stored, new TypeReference<>() {
                  }));
               } catch (JsonProcessingException jpe) {
                  // Microcks writes the outputs as JSON.
               }
            }
         }
         return outputs;
      }
   }
}
