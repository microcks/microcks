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
package io.microcks.domain.arazzo;

/**
 * A call the mocks received in a verification session, and the step of the workflow it was matched to.
 * @param index      The place of the call in the order the session received its calls, from 0
 * @param apiName    The name of the Microcks API whose mocks received the call
 * @param apiVersion The version of that API
 * @param method     The method of the call
 * @param path       The path the call was sent to, after the name and version of the API
 * @param stepId     The step of the workflow the call was matched to; null when it matches none
 * @author SebastienDegodez
 */
public record WorkflowSessionCall(long index, String apiName, String apiVersion, String method, String path,
      String stepId) {
}
