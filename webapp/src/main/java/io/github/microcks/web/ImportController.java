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

import io.github.microcks.service.ImportExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * A Controller for importing new definitions into microcks repository.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ImportController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ImportController.class);

   @Autowired
   private ImportExportService importExportService;

   @RequestMapping(value = "/import", method = RequestMethod.POST)
   public ResponseEntity<?> importRepository(@RequestParam(value = "file") MultipartFile file) {
      log.debug("Importing new services and resources definitions");
      if (!file.isEmpty()) {
         log.debug("Content type of " + file.getOriginalFilename() + " is " + file.getContentType());
         if (MediaType.APPLICATION_JSON_VALUE.equals(file.getContentType())) {
            try {
               byte[] bytes = file.getBytes();
               String json = new String(bytes);
               importExportService.importRepository(json);
            } catch (Exception e) {
               log.error(e.getMessage());
            }
         }
      }
      return new ResponseEntity<Object>(HttpStatus.CREATED);
   }
}
