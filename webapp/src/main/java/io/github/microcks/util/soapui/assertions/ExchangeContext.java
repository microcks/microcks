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
package io.github.microcks.util.soapui.assertions;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;

import java.util.List;

/**
 * Simple record wrapping elements of an exchange context during a test
 * @param service     The Service the test was issued for
 * @param operation   The Operation the test was issued for
 * @param resources   The Resources attached to Service
 * @param resourceUrl A base resource url to fetch relative dependencies that may be found in resources
 */
public record ExchangeContext(Service service, Operation operation, List<Resource> resources, String resourceUrl) {
}

