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
package io.github.microcks.repository;

import io.github.microcks.domain.InvocationLogEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Implementation for InvocationLogRepository
 */
@Repository
public class InvocationLogRepositoryImpl extends SimpleMongoRepository<InvocationLogEntry, Long>
      implements InvocationLogRepository {

   @Autowired
   private MongoTemplate mongoTemplate;

   public InvocationLogRepositoryImpl(MongoOperations mongoOperations) {
      super(new MongoEntityInformation<>() {
         @Override
         @NonNull
         public Class<InvocationLogEntry> getJavaType() {
            return InvocationLogEntry.class;
         }

         @Override
         public boolean isNew(@NonNull InvocationLogEntry entity) {
            return false;
         }

         @Override
         public Long getId(@NonNull InvocationLogEntry entity) {
            return entity.getInvocationTimestamp().toInstant().toEpochMilli();
         }

         @Override
         @NonNull
         public Class<Long> getIdType() {
            return Long.class;
         }

         @Override
         @NonNull
         public String getCollectionName() {
            return "invocations";
         }

         @Override
         @NonNull
         public String getIdAttribute() {
            return "invocationTimestamp";
         }

         @Override
         public Collation getCollation() {
            return null;
         }
      }, mongoOperations);
   }

   /**
    * find the latest invocation log entries from database
    * @param service Service to query
    * @param version Version of the service to query
    * @param limit   maximum number of entries
    * @return List of log entries ordered by newest entry first
    */
   @Override
   public List<InvocationLogEntry> findLastEntriesByServiceName(String service, String version, int limit) {
      Query query = new Query().addCriteria(Criteria.where("serviceName").is(service))
            .addCriteria(Criteria.where("serviceVersion").is(version))
            .with(Sort.by(Sort.Direction.DESC, "invocationTimestamp")).limit(limit);
      return mongoTemplate.find(query, InvocationLogEntry.class);
   }
}
