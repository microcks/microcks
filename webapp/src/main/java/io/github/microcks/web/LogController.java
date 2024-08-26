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

import io.github.microcks.domain.InvocationLogEntry;
import io.github.microcks.repository.InvocationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Endpoint to for accessing the log of the last mock invocations.
 */
@RestController
@ConditionalOnProperty(name = "mocks.enable-invocation-logs", havingValue = "true")
@RequestMapping("/api")
public class LogController {

   @Autowired
   private InvocationLogRepository invocationLogRepository;

   /**
    * get the latest invocations from database
    * @param service Service to be fetched
    * @param version Version of the service to be fetched
    * @param limit   maximum number of latest results
    * @return list of results ordered by newest entry first
    */
   @GetMapping(value = "/log/invocations/{service}/{version}")
   public List<InvocationLogEntry> logInvocations(@PathVariable("service") String service,
         @PathVariable("version") String version, @RequestParam(value = "limit", defaultValue = "10") int limit) {
      return invocationLogRepository.findLastEntriesByServiceName(service, version, limit).stream()
            .sorted(Comparator.comparing(InvocationLogEntry::getInvocationTimestamp).reversed()).toList();
   }
}
