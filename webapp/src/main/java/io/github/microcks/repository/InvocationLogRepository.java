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
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;
import java.util.List;

/**
 * Repository for InvocationLogEntries
 */
@NoRepositoryBean
public interface InvocationLogRepository extends MongoRepository<InvocationLogEntry, Long> {

   /**
    * find the latest invocation log entries from database
    * @param service Service to query
    * @param version Version of the service to query
    * @param limit   maximum number of entries
    * @return List of log entries ordered by newest entry first
    */
   List<InvocationLogEntry> findLastEntriesByServiceName(String service, String version, int limit);
}
