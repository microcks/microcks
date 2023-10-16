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

import io.github.microcks.domain.GenericResource;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * @author laurent
 */
public class GenericResourceRepositoryImpl implements CustomGenericResourceRepository {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GenericResourceRepositoryImpl.class);

   @Autowired
   private MongoTemplate template;

   @Override
   public List<GenericResource> findByServiceIdAndJSONQuery(String serviceId, String jsonQuery) {
      // First parse query document and prepare a list of key to rename then remove.
      Document query = Document.parse(jsonQuery);
      ArrayList<String> keysToRemove = new ArrayList<>();

      // Collect the keys of document that should be updated.
      for (String key : query.keySet()) {
         keysToRemove.add(key);
      }
      // Prefix all keys by payload. that is the nested document where we put resource in
      // and remove all modified keys.
      for (String keyToRemove : keysToRemove) {
         query.append("payload." + keyToRemove, query.get(keyToRemove));
         query.remove(keyToRemove);
      }

      // Finally, append serviceId criterion before launching selection.
      query.append("serviceId", serviceId);

      return template.find(new BasicQuery(query.toJson()), GenericResource.class);
   }
}
