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
 * Provider of the SOAP over HTTP test runner.
 * @author laurent
 */
public class SoapHttpTestRunnerProvider implements TestRunnerProvider {

   @Override
   public TestRunnerType runnerType() {
      return TestRunnerType.SOAP_HTTP;
   }

   @Override
   public AbstractTestRunner<HttpMethod> create(TestRunnerContext context) {
      SoapHttpTestRunner runner = new SoapHttpTestRunner(context.resourceReader());
      runner.setClientHttpRequestFactory(context.requestFactory());
      runner.setResourceUrl(context.resourceUrl());
      runner.setSecret(context.secret());
      return runner;
   }
}
