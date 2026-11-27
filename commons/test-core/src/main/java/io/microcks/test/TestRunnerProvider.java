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

/**
 * Service provider interface contributed by modules to be discovered by the {@link TestRunnerFactory} through
 * {@link java.util.ServiceLoader}. Implementations must be declared in
 * <code>META-INF/services/io.microcks.test.TestRunnerProvider</code>.
 * @author laurent
 */
public interface TestRunnerProvider {

   /**
    * Get the type of runner this provider builds.
    * @return The runner type
    */
   TestRunnerType runnerType();

   /**
    * Create and configure a runner.
    * @param context The context holding collaborators and settings
    * @return A new configured runner
    */
   AbstractTestRunner<HttpMethod> create(TestRunnerContext context);
}
