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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * A Controller for getting an export of microcks repository.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ExportController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ExportController.class);

   @Autowired
   private ImportExportService importExportService;

   @RequestMapping(value = "/export", method = RequestMethod.GET)
   public ResponseEntity<?> exportRepository(@RequestParam(value = "serviceIds") List<String> serviceIds) {
      log.debug("Extracting export for serviceIds {}", serviceIds);
      String json = importExportService.exportRepository(serviceIds, "json");

      byte[] body = json.getBytes();
      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.setContentType(MediaType.APPLICATION_JSON);
      responseHeaders.set("Content-Disposition", "attachment; filename=microcks-repository.json");
      responseHeaders.setContentLength(body.length);

      return new ResponseEntity<Object>(body, responseHeaders, HttpStatus.OK);
   }
}
