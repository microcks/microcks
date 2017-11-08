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
package io.github.microcks.repository;

import io.github.microcks.domain.Service;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation for CustomServiceRepository.
 * @author laurent
 */
public class ServiceRepositoryImpl implements CustomServiceRepository {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ServiceRepositoryImpl.class);

   @Autowired
   private MongoTemplate template;

   @Override
   public List<Service> findByIdIn(List<String> ids) {
      // Convert ids into BSON ObjectId.
      List<ObjectId> objIds = new ArrayList<ObjectId>();
      for (String id : ids){
         objIds.add(new ObjectId(id));
      }

      List<Service> results = template.find(
            new Query(Criteria.where("_id").in(objIds)),
            Service.class);

      return results;
   }
}
