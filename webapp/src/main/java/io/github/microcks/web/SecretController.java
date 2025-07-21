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
import io.github.microcks.security.AuthorizationChecker;
import io.github.microcks.security.UserInfo;
import io.github.microcks.util.SafeLogger;

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

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(SecretController.class);

   private final SecretRepository secretRepository;
   private final AuthorizationChecker authorizationChecker;

   /**
    * Build a new SecretController with its dependencies.
    * @param secretRepository     to have access to Secrets definition
    * @param authorizationChecker to check user authorization
    */
   public SecretController(SecretRepository secretRepository, AuthorizationChecker authorizationChecker) {
      this.secretRepository = secretRepository;
      this.authorizationChecker = authorizationChecker;
   }

   @GetMapping(value = "/secrets")
   public List<Secret> listSecrets(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size, UserInfo userInfo) {
      log.debug("Getting secrets list for page {} and size {}", page, size);

      if (!authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)) {
         return secretRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))).getContent()
               .stream().map(this::filterSensitiveData).toList();
      }
      // We're admin, so we can return all secrets with sensitive data.
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

   @GetMapping(value = "/secrets/{id}")
   public ResponseEntity<Secret> getSecret(@PathVariable("id") String secretId, UserInfo userInfo) {
      log.debug("Getting secret with id {}", secretId);

      if (!authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)) {
         Secret secret = secretRepository.findById(secretId).orElse(null);
         if (secret != null) {
            return new ResponseEntity<>(filterSensitiveData(secret), HttpStatus.OK);
         }
         return new ResponseEntity<>(null, HttpStatus.OK);
      }
      // We're admin, so we can return all secrets with sensitive data.
      return new ResponseEntity<>(secretRepository.findById(secretId).orElse(null), HttpStatus.OK);
   }

   @PutMapping(value = "/secrets/{id}")
   public ResponseEntity<Secret> saveSecret(@PathVariable("id") String secretId, @RequestBody Secret secret) {
      log.debug("Saving existing secret: {}", secret);
      return new ResponseEntity<>(secretRepository.save(secret), HttpStatus.OK);
   }

   @DeleteMapping(value = "/secrets/{id}")
   public ResponseEntity<String> deleteService(@PathVariable("id") String secretId) {
      log.debug("Removing secret with id {}", secretId);
      secretRepository.deleteById(secretId);
      return new ResponseEntity<>(HttpStatus.OK);
   }

   /** Filter sensitive data from a Secret before returning it to the user. */
   private Secret filterSensitiveData(Secret secret) {
      secret.setUsername(null);
      secret.setPassword(null);
      secret.setToken(null);
      secret.setCaCertPem(null);
      return secret;
   }
}
