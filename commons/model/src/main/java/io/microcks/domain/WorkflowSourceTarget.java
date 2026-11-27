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
package io.microcks.domain;

/**
 * Where a run calls the operations of a source description, and the Microcks API it is linked to, kept by reference so
 * that the run stays readable once the API is deleted.
 * @param sourceName The name of the source description
 * @param serviceId  The id of the linked API
 * @param apiName    The name of the linked API
 * @param apiVersion The version of the linked API
 * @param baseUrl    The base URL the operations are called at: relative to Microcks for its mocks
 * @author SebastienDegodez
 */
public record WorkflowSourceTarget(String sourceName, String serviceId, String apiName, String apiVersion,
      String baseUrl) {
}
