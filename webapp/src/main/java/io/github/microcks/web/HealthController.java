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

import io.github.microcks.domain.ImportJob;
import io.github.microcks.repository.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class HealthController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(HealthController.class);

   @Autowired
   private ImportJobRepository jobRepository;

   @RequestMapping(value = "/health", method = RequestMethod.GET)
   public ResponseEntity<String> health() {
      log.trace("Health check endpoint invoked");

      try {
         // Using a single selection query to ensure connection to MongoDB is ok.
         List<ImportJob> jobs = jobRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "name")))
               .getContent();
      } catch (Exception e) {
         log.error("Health check caught an exception: " + e.getMessage(), e);
         return new ResponseEntity<String>(HttpStatus.SERVICE_UNAVAILABLE);
      }
      log.trace("Health check is OK");
      return new ResponseEntity<String>(HttpStatus.OK);
   }
}
