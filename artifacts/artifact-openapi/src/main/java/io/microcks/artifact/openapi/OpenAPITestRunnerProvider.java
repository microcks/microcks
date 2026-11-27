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
package io.microcks.artifact.openapi;

import io.microcks.domain.TestRunnerType;
import io.microcks.util.http.HttpMethod;
import io.microcks.test.AbstractTestRunner;
import io.microcks.test.TestRunnerContext;
import io.microcks.test.TestRunnerProvider;

/**
 * Provider of the OpenAPI schema test runner.
 * @author laurent
 */
public class OpenAPITestRunnerProvider implements TestRunnerProvider {

   @Override
   public TestRunnerType runnerType() {
      return TestRunnerType.OPEN_API_SCHEMA;
   }

   @Override
   public AbstractTestRunner<HttpMethod> create(TestRunnerContext context) {
      OpenAPITestRunner runner = new OpenAPITestRunner(context.resourceReader(), context.responseReader(), true);
      runner.setClientHttpRequestFactory(context.requestFactory());
      runner.setResourceUrl(context.resourceUrl());
      runner.setSecret(context.secret());
      return runner;
   }
}
