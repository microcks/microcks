/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.testsupport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.callback.QuarkusTestBeforeClassCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.TestInstance;

/**
 * Replaces Spring's {@code @DirtiesContext}/in-memory Mongo isolation: empties the test database before each test class
 * and before each test method (except for {@code PER_CLASS} test instances that prepare data in {@code @BeforeAll}).
 * @author laurent
 */
public class MongoCleanupCallback implements QuarkusTestBeforeClassCallback, QuarkusTestBeforeEachCallback {

   @Override
   public void beforeClass(Class<?> testClass) {
      clean();
   }

   @Override
   public void beforeEach(QuarkusTestMethodContext context) {
      TestInstance lifecycle = context.getTestInstance().getClass().getAnnotation(TestInstance.class);
      if (lifecycle == null || lifecycle.value() != TestInstance.Lifecycle.PER_CLASS) {
         clean();
      }
   }

   private void clean() {
      if (Arc.container() == null) {
         return;
      }
      MongoClient client = Arc.container().instance(MongoClient.class).get();
      String name = ConfigProvider.getConfig().getValue("quarkus.mongodb.database", String.class);
      MongoDatabase database = client.getDatabase(name);
      for (String collection : database.listCollectionNames()) {
         database.getCollection(collection).deleteMany(new org.bson.Document());
      }
   }
}
