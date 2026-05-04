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

import io.github.microcks.domain.ImportJob;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

/**
 * Implementation for CustomImportJobRepository.
 * @author laurent
 */
public class ImportJobRepositoryImpl implements CustomImportJobRepository {

   private MongoTemplate template;

   public ImportJobRepositoryImpl(MongoTemplate template) {
      this.template = template;
   }

   @Override
   public List<ImportJob> findByLabels(Map<String, String> labels) {
      Query query = new Query();
      for (Map.Entry<String, String> entry : labels.entrySet()) {
         query.addCriteria(Criteria.where("metadata.labels." + entry.getKey()).is(entry.getValue()));
      }
      return template.find(query, ImportJob.class);
   }

   @Override
   public List<ImportJob> findByLabelsAndNameLike(Map<String, String> labels, String name) {
      Query query = new Query(Criteria.where("name").regex(name, "i"));
      for (Map.Entry<String, String> entry : labels.entrySet()) {
         query.addCriteria(Criteria.where("metadata.labels." + entry.getKey()).is(entry.getValue()));
      }
      return template.find(query, ImportJob.class);
   }
}
