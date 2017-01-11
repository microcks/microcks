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
package com.github.lbroudoux.microcks.web;

import com.github.lbroudoux.microcks.domain.Resource;
import com.github.lbroudoux.microcks.repository.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * A controller for mocking Soap responses.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ResourceController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ResourceController.class);

   @Autowired
   private ResourceRepository resourceRepository;


   @RequestMapping(value = "/resources/{name}", method = RequestMethod.GET)
   public ResponseEntity<?> execute(
         @PathVariable("name") String name,
         HttpServletRequest request
   ) {
      String extension = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('.'));
      name = name + extension;

      log.info("Requesting resource named " + name);

      Resource resource = resourceRepository.findByName(name);
      if (resource != null){
         return new ResponseEntity<Object>(resource.getContent(), HttpStatus.OK);
      }
      return new ResponseEntity<Object>(HttpStatus.NOT_FOUND);
   }
}
