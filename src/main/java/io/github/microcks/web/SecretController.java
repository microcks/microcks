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
package io.github.microcks.web;

import io.github.microcks.domain.Secret;
import io.github.microcks.repository.SecretRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Rest controller for API defined on services.
 * @author laurent
 */
@RestController
@RequestMapping("/api")
public class SecretController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SecretController.class);

   @Autowired
   private SecretRepository secretRepository;


   @RequestMapping(value = "/secrets", method = RequestMethod.GET)
   public List<Secret> listSecrets(
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size
   ) {
      log.debug("Getting secrets list for page {} and size {}", page, size);
      return secretRepository.findAll(new PageRequest(page, size,
            new Sort(Sort.Direction.ASC, "name"))).getContent();
   }

   @RequestMapping(value = "/secrets/search", method = RequestMethod.GET)
   public List<Secret> searchSecrets(@RequestParam(value = "name") String name) {
      log.debug("Searching secrets corresponding to {}", name);
      return secretRepository.findByNameLike(name);
   }

   @RequestMapping(value = "/secrets/count", method = RequestMethod.GET)
   public Map<String, Long> countSecrets() {
      log.debug("Counting secrets...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", secretRepository.count());
      return counter;
   }

   @RequestMapping(value = "/secrets", method = RequestMethod.POST)
   public ResponseEntity<Secret> createSecret(@RequestBody Secret secret) {
      log.debug("Creating new secret: {}", secret);
      return new ResponseEntity<>(secretRepository.save(secret), HttpStatus.CREATED);
   }

   @RequestMapping(value = "/secrets/{id}", method = RequestMethod.PUT)
   public ResponseEntity<Secret> saveSecret(@RequestBody Secret secret) {
      log.debug("Saving existing secret: {}", secret);
      return new ResponseEntity<>(secretRepository.save(secret), HttpStatus.OK);
   }

   @RequestMapping(value = "/secrets/{id}", method = RequestMethod.DELETE)
   public ResponseEntity<String> deleteService(@PathVariable("id") String secretId) {
      log.debug("Removing secret with id {}", secretId);
      secretRepository.delete(secretId);
      return new ResponseEntity<>(HttpStatus.OK);
   }
}
