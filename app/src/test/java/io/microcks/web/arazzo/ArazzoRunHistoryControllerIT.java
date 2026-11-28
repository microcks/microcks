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
package io.microcks.web.arazzo;

import io.microcks.web.AbstractBaseIT;
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.repository.workflow.WorkflowResultRepository;
import io.microcks.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the runs history endpoints on MongoDB: the runs of <i>API Pastry scenarios</i> are read newest
 * first, filtered and counted as the history page asks.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunHistoryControllerIT extends AbstractBaseIT {

   private static final String RUNS = "target/test-classes/io/microcks/service/arazzo/pastry-scenarios.runs.yaml";
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @BeforeEach
   void runsOfPastryScenarios() throws Exception {
      try (MappingIterator<WorkflowResult> runs = ObjectMapperFactory.getYamlObjectMapper()
            .readerFor(WorkflowResult.class).readValues(new File(RUNS))) {
         workflowResultRepository.persistOrUpdate(runs.readAll());
      }
   }

   @AfterEach
   void deleteRuns() {
      workflowResultRepository.deleteAll();
   }

   @Test
   void runsAreListedNewestFirstWithTheirOutcome() throws Exception {
      JsonNode runs = get("/api/workflows/documents/API Pastry scenarios/runs");

      assertEquals(List.of("#3 listThenReadFirst MOCKS FAILURE readFirst 15 2026-10-21T10:00:00Z",
            "#2 readThenReprice IMPLEMENTATIONS FAILURE reprice 58 2026-10-21T09:00:00Z",
            "#1 readThenReprice MOCKS SUCCESS none 24 2026-10-20T09:00:00Z"), described(runs));
   }

   @Test
   void runsAreFilteredByWorkflowTargetAndResultThenPaged() throws Exception {
      assertEquals(List.of("#3", "#2"), numbers(get(
            "/api/workflows/documents/API Pastry scenarios/runs?result=FAILURE")));
      assertEquals(List.of("#2"), numbers(
            get("/api/workflows/documents/API Pastry scenarios/runs?workflow=readThenReprice&target=IMPLEMENTATIONS")));
      assertEquals(List.of("#1"), numbers(get("/api/workflows/documents/API Pastry scenarios/runs?page=1&size=2")));
   }

   @Test
   void runsAreCountedWithThoseThatPassedAndFailed() throws Exception {
      assertEquals("{\"total\":3,\"passed\":1,\"failed\":2}",
            get("/api/workflows/documents/API Pastry scenarios/runs/count").toString());
      assertEquals("{\"total\":2,\"passed\":1,\"failed\":1}",
            get("/api/workflows/documents/API Pastry scenarios/runs/count?workflow=readThenReprice").toString());
   }

   @Test
   void anUnknownTargetOrResultIsABadRequest() {
      assertEquals(400,
            restTemplate.getForEntity("/api/workflows/documents/API Pastry scenarios/runs?target=STAGING", String.class)
                  .getStatusCode().value());
      assertEquals(400,
            restTemplate
                  .getForEntity("/api/workflows/documents/API Pastry scenarios/runs/count?result=SKIPPED", String.class)
                  .getStatusCode().value());
   }

   private JsonNode get(String url) throws Exception {
      return JSON.readTree(restTemplate.getForEntity(url, String.class).getBody());
   }

   private static List<String> numbers(JsonNode runs) {
      List<String> numbers = new ArrayList<>();
      runs.forEach(run -> numbers.add("#" + run.get("number").asLong()));
      return numbers;
   }

   private static List<String> described(JsonNode runs) {
      List<String> described = new ArrayList<>();
      runs.forEach(run -> described.add(
            "#" + run.get("number").asLong() + " " + run.get("workflowId").asText() + " " + run.get("target").asText()
                  + " " + run.get("status").asText() + " " + run.path("failedStepId").asText("none") + " "
                  + run.get("elapsedTime").asLong() + " " + Instant.ofEpochMilli(run.get("startedDate").asLong())));
      return described;
   }
}
