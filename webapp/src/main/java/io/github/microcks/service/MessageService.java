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
package io.github.microcks.service;

import io.github.microcks.domain.*;
import io.github.microcks.repository.EventMessageRepository;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResponseRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service bean for common processing around messages (request and responses).
 * @author laurent
 */
@org.springframework.stereotype.Service
public class MessageService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MessageService.class);

   private final RequestRepository requestRepository;
   private final ResponseRepository responseRepository;
   private final EventMessageRepository eventMessageRepository;

   /**
    * Create a new MessageService with required dependencies.
    * @param requestRepository      The repository for requests
    * @param responseRepository     The repository for responses
    * @param eventMessageRepository The repository for event messages
    */
   public MessageService(RequestRepository requestRepository, ResponseRepository responseRepository,
         EventMessageRepository eventMessageRepository) {
      this.requestRepository = requestRepository;
      this.responseRepository = responseRepository;
      this.eventMessageRepository = eventMessageRepository;
   }


   /**
    * Retrieve unidirectional events corresponding to an Operation.
    * @param operationId The identifier of operation to get messages for.
    * @return A list of unidirectional event messages
    */
   public List<UnidirectionalEvent> getEventByOperation(String operationId) {
      // Retrieve event messages using operation identifier.
      List<EventMessage> eventMessages = eventMessageRepository.findByOperationId(operationId);
      if (log.isDebugEnabled()) {
         log.debug("Found {} event(s) for operation {}", eventMessages.size(), operationId);
      }

      // Just wrap then into an UnidirectionalEvent exchange.
      List<UnidirectionalEvent> results = new ArrayList<>(eventMessages.size());
      for (EventMessage eventMessage : eventMessages) {
         results.add(new UnidirectionalEvent(eventMessage));
      }
      return results;
   }

   /**
    * Retrieve pairs of requests and responses corresponding to an Operation.
    * @param operationId The identifier of operation to get messages for.
    * @return A list of paired requests and responses
    */
   public List<RequestResponsePair> getRequestResponseByOperation(String operationId) {
      // Retrieve requests and responses using operation identifier.
      List<Request> requests = requestRepository.findByOperationId(operationId);
      List<Response> responses = responseRepository.findByOperationId(operationId);
      if (log.isDebugEnabled()) {
         log.debug("Found {} request(s) for operation {}", requests.size(), operationId);
         log.debug("Found {} response(s) for operation {}", responses.size(), operationId);
      }

      // Browse them to reassociate them.
      List<RequestResponsePair> results = associatePairs(requests, responses);
      if (log.isDebugEnabled()) {
         log.debug("Emitting {} request/response pair(s) as result", results.size());
      }
      return results;
   }

   /**
    * Retrieve unidirectional events corresponding to a TestCase.
    * @param testCaseId The identifier of test case to get messages for.
    * @return A list of unidirectional event messages
    */
   public List<UnidirectionalEvent> getEventByTestCase(String testCaseId) {
      // Retrieve events using testCase identifier.
      List<EventMessage> eventMessages = eventMessageRepository.findByTestCaseId(testCaseId);
      if (log.isDebugEnabled()) {
         log.debug("Found {} event(s) for testCase {}", eventMessages.size(), testCaseId);
      }

      // Just wrap then into an UnidirectionalEvent exchange.
      List<UnidirectionalEvent> results = new ArrayList<>(eventMessages.size());
      for (EventMessage eventMessage : eventMessages) {
         results.add(new UnidirectionalEvent(eventMessage));
      }
      return results;
   }

   /**
    * Retrieved pairs of requests and responses corresponding to a TestCase.
    * @param testCaseId The identifier of test case to get messages for.
    * @return A list of paired requests and responses
    */
   public List<RequestResponsePair> getRequestResponseByTestCase(String testCaseId) {
      // Retrieve requests and responses using testCase identifier.
      List<Request> requests = requestRepository.findByTestCaseId(testCaseId);
      List<Response> responses = responseRepository.findByTestCaseId(testCaseId);
      if (log.isDebugEnabled()) {
         log.debug("Found {} request(s) for testCase {}", requests.size(), testCaseId);
         log.debug("Found {} response(s) for testCase {}", responses.size(), testCaseId);
      }

      // Browse them to reassociate them.
      List<RequestResponsePair> results = associatePairs(requests, responses);
      if (log.isDebugEnabled()) {
         log.debug("Emitting {} request/response pair(s) as result", results.size());
      }
      return results;
   }

   /** */
   private List<RequestResponsePair> associatePairs(List<Request> requests, List<Response> responses) {
      List<RequestResponsePair> results = new ArrayList<>();

      // Browse them to reassociate them.
      for (Request request : requests) {
         for (Response response : responses) {
            if (request.getResponseId() != null && request.getResponseId().equals(response.getId())) {
               // If found a matching response, build a pair.
               results.add(new RequestResponsePair(request, response));
               break;
            }
         }
      }
      return results;
   }
}
