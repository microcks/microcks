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
package io.microcks.test;

import io.microcks.domain.TestRunnerType;
import io.microcks.util.http.HttpMethod;

import java.util.ServiceLoader;

/**
 * Factory retrieving the test runner for a {@link TestRunnerType} among the {@link TestRunnerProvider} discovered
 * through {@link ServiceLoader}. Falls back to the plain HTTP runner for unknown types.
 * @author laurent
 */
public class TestRunnerFactory {

   private TestRunnerFactory() {
      // Private constructor to hide the implicit one as it's a utility class.
   }

   /**
    * Create the runner matching the requested type.
    * @param runnerType The type of runner
    * @param context    The context used to build and configure the runner
    * @return A configured test runner
    */
   public static AbstractTestRunner<HttpMethod> getTestRunner(TestRunnerType runnerType, TestRunnerContext context) {
      TestRunnerProvider fallback = null;
      // Runners are created from ForkJoinPool threads whose context class loader does not see the application
      // classes in Quarkus dev mode: look the providers up with the loader of the SPI itself.
      for (TestRunnerProvider provider : ServiceLoader.load(TestRunnerProvider.class,
            TestRunnerProvider.class.getClassLoader())) {
         if (provider.runnerType() == runnerType) {
            return provider.create(context);
         }
         if (provider.runnerType() == TestRunnerType.HTTP) {
            fallback = provider;
         }
      }
      if (fallback == null) {
         throw new IllegalStateException("No test runner registered for " + runnerType + " and no HTTP runner");
      }
      return fallback.create(context);
   }
}
