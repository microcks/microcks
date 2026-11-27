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
package io.microcks.service;

import io.microcks.domain.ArazzoSourceMatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests of what the matcher of a source description leaves behind once it has read the declared contract. The match
 * itself is covered by the acceptance tests of the import dialog.
 * @author SebastienDegodez
 */
class ArazzoSourceMatcherTest {

   @TempDir
   private Path downloads;

   @Test
   void theDownloadedContractIsDeletedOnceRead() throws Exception {
      Path contract = Files.writeString(downloads.resolve("contract.download"), "openapi: 3.0.2\n");
      ArazzoSourceMatcher matcher = new ArazzoSourceMatcher(null, url -> contract.toFile());

      ArazzoSourceMatch match = matcher.match("https://contracts.example.com/customer-api.openapi.yaml");

      assertEquals(ArazzoSourceMatch.Status.UNREADABLE, match.status());
      assertFalse(Files.exists(contract));
   }
}
