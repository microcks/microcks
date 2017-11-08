/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.service;

import io.github.microcks.domain.Request;
import io.github.microcks.domain.Response;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Service bean for common processing around messages (request and responses).
 * @author laurent
 */
@org.springframework.stereotype.Service
public class MessageService {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(MessageService.class);

   @Autowired
   private RequestRepository requestRepository;

   @Autowired
   private ResponseRepository responseRepository;


   /**
    * Retrieve pairs of requests and responses corresponding to an Operation.
    * @param operationId The identifier of operation to get messages for.
    * @return A list of paired requests and responses
    */
   public List<RequestResponsePair> getRequestResponseByOperation(String operationId) {
      // Retrieve requests and responses using operation identifier.
      List<Request> requests = requestRepository.findByOperationId(operationId);
      List<Response> responses = responseRepository.findByOperationId(operationId);
      if (log.isDebugEnabled()){
         log.debug("Found " + requests.size() + " request(s) for operation " + operationId);
         log.debug("Found " + responses.size() + " response(s) for operation " + operationId);
      }

      // Browse them to reassociate them.
      List<RequestResponsePair> results = associatePairs(requests, responses);
      if (log.isDebugEnabled()){
         log.debug("Emitting " + results.size() + " request/response pair(s) as result");
      }
      return results;
   }

   /**
    * Retrieved pairs of requests and responses corresponding to a TestCase?
    * @param testCaseId The identifier of test case to get messages for.
    * @return A list of paired requests and responses
    */
   public List<RequestResponsePair> getRequestResponseByTestCase(String testCaseId) {
      // Retrieve requests and responses using testCase identifier.
      List<Request> requests = requestRepository.findByTestCaseId(testCaseId);
      List<Response> responses = responseRepository.findByTestCaseId(testCaseId);
      if (log.isDebugEnabled()){
         log.debug("Found " + requests.size() + " request(s) for testCase " + testCaseId);
         log.debug("Found " + responses.size() + " response(s) for testCase " + testCaseId);
      }

      // Browse them to reassociate them.
      List<RequestResponsePair> results = associatePairs(requests, responses);
      if (log.isDebugEnabled()){
         log.debug("Emitting " + results.size() + " request/response pair(s) as result");
      }
      return results;
   }

   /** */
   private List<RequestResponsePair> associatePairs(List<Request> requests, List<Response> responses){
      List<RequestResponsePair> results = new ArrayList<RequestResponsePair>();

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
