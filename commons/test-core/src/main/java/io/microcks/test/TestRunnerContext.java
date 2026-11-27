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

import io.microcks.domain.Secret;
import io.microcks.util.http.ClientHttpRequestFactory;

/**
 * Everything a {@link TestRunnerProvider} may need to build and configure a test runner. Fields not relevant for a
 * given runner type may be null.
 * @param resourceReader   Access to the resources of the tested service
 * @param responseReader   Access to the responses of the tested service
 * @param secretReader     Access to the secrets
 * @param requestFactory   The HTTP request factory to be used by HTTP based runners
 * @param secret           The secret to use when connecting to the tested endpoint
 * @param timeout          The runner timeout in milliseconds
 * @param resourceUrl      The URL used to resolve validation resources
 * @param asyncMinionUrl   The URL of the async minion (AsyncAPI runner)
 * @param testsCallbackUrl The URL called back with Postman tests results
 * @param postmanRunnerUrl The URL of the Postman runner
 * @author laurent
 */
public record TestRunnerContext(ResourceReader resourceReader, ResponseReader responseReader, SecretReader secretReader,
      ClientHttpRequestFactory requestFactory, Secret secret, Long timeout, String resourceUrl, String asyncMinionUrl,
      String testsCallbackUrl, String postmanRunnerUrl) {
}
