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
package com.github.lbroudoux.microcks.repository;

import com.github.lbroudoux.microcks.domain.Service;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.math.BigInteger;
import java.util.List;

/**
 * Repository interface for Service domain objects.
 * @author laurent
 */
public interface ServiceRepository extends MongoRepository<Service, String>, CustomServiceRepository {

   Service findByNameAndVersion(String name, String version);

   @Query("{'name' : {'$regex':?0, '$options':'i'}}")
   List<Service> findByNameLike(String name);
}