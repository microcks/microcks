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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * A Controller for getting an export of microcks repository.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ExportController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(ExportController.class);

   private final ImportExportService importExportService;

   /**
    * Build a new ExportController with its dependencies.
    * @param importExportService to have access to export service
    */
   public ExportController(ImportExportService importExportService) {
      this.importExportService = importExportService;
   }

   @GetMapping(value = "/export")
   public ResponseEntity<Object> exportRepository(@RequestParam(value = "serviceIds") List<String> serviceIds) {
      log.debug("Extracting export for serviceIds {}", serviceIds);
      String json = importExportService.exportRepository(serviceIds, "json");

      byte[] body = json.getBytes();
      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.setContentType(MediaType.APPLICATION_JSON);
      responseHeaders.set("Content-Disposition", "attachment; filename=microcks-repository.json");
      responseHeaders.setContentLength(body.length);

      return new ResponseEntity<>(body, responseHeaders, HttpStatus.OK);
   }
}
