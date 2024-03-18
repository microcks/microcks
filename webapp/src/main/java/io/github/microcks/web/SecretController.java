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

import io.github.microcks.domain.Secret;
import io.github.microcks.repository.SecretRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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


   @GetMapping(value = "/secrets")
   public List<Secret> listSecrets(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
      log.debug("Getting secrets list for page {} and size {}", page, size);
      return secretRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))).getContent();
   }

   @GetMapping(value = "/secrets/search")
   public List<Secret> searchSecrets(@RequestParam(value = "name") String name) {
      log.debug("Searching secrets corresponding to {}", name);
      return secretRepository.findByNameLike(name);
   }

   @GetMapping(value = "/secrets/count")
   public Map<String, Long> countSecrets() {
      log.debug("Counting secrets...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", secretRepository.count());
      return counter;
   }

   @PostMapping(value = "/secrets")
   public ResponseEntity<Secret> createSecret(@RequestBody Secret secret) {
      log.debug("Creating new secret: {}", secret);
      return new ResponseEntity<>(secretRepository.save(secret), HttpStatus.CREATED);
   }

   @PutMapping(value = "/secrets/{id}")
   public ResponseEntity<Secret> saveSecret(@RequestBody Secret secret) {
      log.debug("Saving existing secret: {}", secret);
      return new ResponseEntity<>(secretRepository.save(secret), HttpStatus.OK);
   }

   @DeleteMapping(value = "/secrets/{id}")
   public ResponseEntity<String> deleteService(@PathVariable("id") String secretId) {
      log.debug("Removing secret with id {}", secretId);
      secretRepository.deleteById(secretId);
      return new ResponseEntity<>(HttpStatus.OK);
   }
}
