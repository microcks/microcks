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

import io.github.microcks.domain.Service;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.MockRepositoryImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

/**
 * @author laurent
 */
@RestController
@RequestMapping("/api")
public class UploadArtifactController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(UploadArtifactController.class);

   @Autowired
   private ServiceService serviceService;


   @RequestMapping(value = "/artifact/upload", method = RequestMethod.POST)
   public ResponseEntity<?> importArtifact(@RequestParam(value = "file") MultipartFile file) {
      if (!file.isEmpty()) {
         log.debug("Content type of " + file.getOriginalFilename() + " is " + file.getContentType());

         List<Service> services = null;

         try {
            // Save upload to local file before import.
            String localFile = System.getProperty("java.io.tmpdir") + "/microcks-" + System.currentTimeMillis() + ".artifact";

            ReadableByteChannel rbc = null;
            FileOutputStream fos = null;
            try {
               rbc = Channels.newChannel(file.getInputStream());
               // Transfer file to local.
               fos = new FileOutputStream(localFile);
               fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }
            finally {
               if (fos != null)
                  fos.close();
               if (rbc != null)
                  rbc.close();
            }

            // Now try importing services.
            services = serviceService.importServiceDefinition(new File(localFile));
         } catch (IOException ioe) {
            log.error("Exception while writing uploaded item " + file.getOriginalFilename(), ioe);
            return new ResponseEntity<Object>("Exception while writing uploaded item", HttpStatus.INTERNAL_SERVER_ERROR);
         } catch (MockRepositoryImportException mrie) {
            return new ResponseEntity<Object>(mrie.getMessage(), HttpStatus.BAD_REQUEST);
         }
         if (services != null && services.size() > 0) {
            return new ResponseEntity<Object>("Imported " + services.get(0).getName() + "-" + services.get(0).getVersion(), HttpStatus.CREATED);
         }
      }
      return new ResponseEntity<Object>(HttpStatus.NO_CONTENT);
   }
}
