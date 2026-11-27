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
package io.microcks.repository;

import io.microcks.domain.DailyStatistic;
import io.microcks.domain.ImportJob;
import io.microcks.domain.Metadata;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceRef;
import io.microcks.domain.ServiceState;
import io.microcks.domain.ServiceType;
import io.microcks.domain.TestConformanceMetric;
import io.microcks.domain.TestResult;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Panache MongoDB repositories against a real MongoDB (Dev Services).
 * @author laurent
 */
@QuarkusTest
class PanacheRepositoriesTest {

   @Inject
   MongoClient mongoClient;
   @Inject
   ServiceRepository services;
   @Inject
   ResponseRepository responses;
   @Inject
   ImportJobRepository importJobs;
   @Inject
   TestResultRepository testResults;
   @Inject
   DailyStatisticRepository dailyStatistics;
   @Inject
   TestConformanceMetricRepository conformanceMetrics;
   @Inject
   ServiceStateRepository serviceStates;

   @BeforeEach
   void clean() {
      services.deleteAll();
      responses.deleteAll();
      importJobs.deleteAll();
      testResults.deleteAll();
      dailyStatistics.deleteAll();
      conformanceMetrics.deleteAll();
      serviceStates.deleteAll();
   }

   private Service service(String name, ServiceType type, Map<String, String> labels) {
      Service service = new Service();
      service.setName(name);
      service.setVersion("1.0");
      service.setType(type);
      Metadata metadata = new Metadata();
      metadata.setLabels(labels);
      service.setMetadata(metadata);
      return service;
   }

   private Document rawFirst(String collection) {
      return mongoClient.getDatabase("microcks").getCollection(collection).find().first();
   }

   @Test
   void persistGeneratesObjectIdInLegacyCollection() {
      Service service = service("Petstore API", ServiceType.REST, Map.of("domain", "pets"));
      services.persist(service);

      assertNotNull(service.getId());
      Document raw = rawFirst("service");
      assertTrue(raw.get("_id") instanceof ObjectId);
      assertEquals(service.getId(), raw.getObjectId("_id").toHexString());
      assertFalse(raw.containsKey("_class"));

      Service found = services.findById(service.getId());
      assertEquals("Petstore API", found.getName());
      assertTrue(services.findByIdOptional(service.getId()).isPresent());
      assertEquals(1, services.findByIdIn(List.of(service.getId())).size());
      assertEquals("Petstore API", services.findByNameAndVersion("Petstore API", "1.0").getName());
   }

   @Test
   void updateAndDeleteUseObjectId() {
      Service service = service("Orders", ServiceType.REST, Map.of());
      services.persist(service);
      service.setName("Orders v2");
      services.update(service);
      assertEquals("Orders v2", services.findById(service.getId()).getName());
      assertEquals(1, services.count());

      services.persistOrUpdate(service);
      assertEquals(1, services.count());

      assertTrue(services.deleteById(service.getId()));
      assertNull(services.findById(service.getId()));
   }

   @Test
   void nameLikeLabelsAndAggregations() {
      services.persist(service("Petstore API", ServiceType.REST, Map.of("domain", "pets", "team", "a")));
      services.persist(service("Pet Events", ServiceType.EVENT, Map.of("domain", "pets")));
      services.persist(service("Billing", ServiceType.GRPC, Map.of("domain", "money")));

      assertEquals(2, services.findByNameLike("^pet").size());
      assertEquals(1, services.findByType(ServiceType.GRPC).size());
      assertEquals(2, services.findByLabels(Map.of("domain", "pets")).size());
      assertEquals(1, services.findByLabelsAndNameLike(Map.of("domain", "pets"), "events").size());
      assertEquals(3, services.findByLabels(Map.of()).size());

      List<CustomServiceRepository.ServiceCount> counts = services.countServicesByType();
      assertEquals(3, counts.size());
      assertEquals(1, counts.get(0).getNumber());

      List<CustomServiceRepository.LabelValues> labels = services.listLabels();
      assertEquals(2, labels.size());
      CustomServiceRepository.LabelValues domain = labels.stream().filter(l -> l.getKey().equals("domain")).findFirst()
            .orElseThrow();
      assertEquals(Set.of("pets", "money"), Set.of(domain.getValues()));
   }

   @Test
   void nonCallbackQueryAndPagination() {
      for (int i = 0; i < 5; i++) {
         Response response = new Response();
         response.setOperationId("op1");
         response.setName("r" + i);
         response.setDispatchCriteria("c");
         if (i == 0) {
            response.setCallbackName("cb");
         }
         responses.persist(response);
      }
      assertEquals(5, responses.findByOperationIdAndDispatchCriteria("op1", "c").size());
      assertEquals(4, responses.findNonCallbackByOperationIdAndDispatchCriteria("op1", "c").size());
      assertEquals(1, responses.findNonCallbackByOperationIdAndName("op1", "r2").size());
      assertEquals(5, responses.findByOperationIdIn(List.of("op1", "op2")).size());

      for (int i = 0; i < 5; i++) {
         TestResult result = new TestResult();
         result.setServiceId("svc");
         result.setTestDate(new Date(1000L * i));
         testResults.persist(result);
      }
      List<TestResult> page = testResults.findByServiceId("svc", Page.of(1, 2), Sort.descending("testDate"));
      assertEquals(2, page.size());
      assertEquals(2000L, page.get(0).getTestDate().getTime());
      assertEquals(5, testResults.countByServiceId("svc"));
      assertEquals(2, testResults.findAllWithTestDateAfter(new Date(2000L)).size());
   }

   @Test
   void importJobElemMatch() {
      ImportJob job = new ImportJob();
      job.setName("job");
      ServiceRef ref = new ServiceRef();
      ref.setServiceId("svc-1");
      job.setServiceRefs(Set.of(ref));
      importJobs.persist(job);

      assertEquals(1, importJobs.findByServiceRefId("svc-1").size());
      assertEquals(0, importJobs.findByServiceRefId("svc-2").size());
      assertEquals(1, importJobs.findByNameLike("JO").size());
   }

   @Test
   void dailyStatisticsIncrementAndAggregate() {
      DailyStatistic statistic = new DailyStatistic();
      statistic.setDay("20260101");
      statistic.setServiceName("svc");
      statistic.setServiceVersion("1.0");
      statistic.setDailyCount(1);
      statistic.setHourlyCount(Map.of("10", 1));
      statistic.setMinuteCount(Map.of("1030", 1));
      dailyStatistics.persist(statistic);

      dailyStatistics.incrementDailyStatistic("20260101", "svc", "1.0", "10", "1030", 2);

      DailyStatistic stored = dailyStatistics.findByDayAndServiceNameAndServiceVersion("20260101", "svc", "1.0").get(0);
      assertEquals(3, stored.getDailyCount());
      assertEquals(3, stored.getHourlyCount().get("10"));
      assertEquals(3, dailyStatistics.aggregateDailyStatistics("20260101").getDailyCount());
      assertEquals(0, dailyStatistics.aggregateDailyStatistics("20260102").getDailyCount());
      assertEquals(1, dailyStatistics.aggregateDailyStatistics("20260101", "20260102").size());
      assertEquals(1, dailyStatistics.findTopStatistics("20260101", 5).size());
   }

   @Test
   void conformanceMetricAggregation() {
      for (double score : new double[] { 50, 100 }) {
         TestConformanceMetric metric = new TestConformanceMetric();
         metric.setServiceId("s" + score);
         metric.setAggregationLabelValue("team-a");
         metric.setCurrentScore(score);
         conformanceMetrics.persist(metric);
      }
      var results = conformanceMetrics.aggregateTestConformanceMetric();
      assertEquals(1, results.size());
      assertEquals(75.0, results.get(0).getValue());
      assertEquals(2, results.get(0).getWeight());
      assertNotNull(conformanceMetrics.findByServiceId("s50.0"));
   }

   @Test
   void serviceStateTtlIndexAndStore() {
      ServiceState state = new ServiceState();
      state.setServiceId("svc");
      state.setKey("k");
      state.setValue("v");
      state.setExpireAt(new Date(System.currentTimeMillis() + 60000));
      serviceStates.persistOrUpdate(state);
      assertEquals("v", serviceStates.findByServiceIdAndKey("svc", "k").getValue());

      boolean ttl = false;
      for (Document index : mongoClient.getDatabase("microcks").getCollection("serviceState").listIndexes()) {
         if (index.get("key", Document.class).containsKey("expireAt")) {
            ttl = Long.valueOf(0).equals(((Number) index.get("expireAfterSeconds")).longValue());
         }
      }
      assertTrue(ttl);
      assertEquals(1,
            mongoClient.getDatabase("microcks").getCollection("serviceState").countDocuments(Filters.eq("key", "k")));
   }
}
