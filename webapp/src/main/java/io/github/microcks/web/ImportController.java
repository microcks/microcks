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
import io.github.microcks.util.SafeLogger;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * A Controller for importing new definitions into microcks repository.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ImportController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(ImportController.class);

   private final ImportExportService importExportService;

   /**
    * Create new ImportController with required service.
    * @param importExportService The service for managing imports.
    */
   public ImportController(ImportExportService importExportService) {
      this.importExportService = importExportService;
   }

   @PostMapping(value = "/import")
   public ResponseEntity<byte[]> importRepository(@RequestParam(value = "file") MultipartFile file) {
      log.debug("Importing new services and resources definitions");
      if (!file.isEmpty()) {
         log.debug("Content type of {} is {}", file.getOriginalFilename(), file.getContentType());
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
      return new ResponseEntity<>(HttpStatus.CREATED);
   }
}
