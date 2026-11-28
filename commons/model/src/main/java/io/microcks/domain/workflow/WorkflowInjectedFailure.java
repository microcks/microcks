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
package io.microcks.domain.workflow;

/**
 * A step of a workflow set to fail in a verification session: in that session, the mocks answer the operation of the
 * step with one of its existing examples instead of dispatching the call.
 * @param stepId        The step set to fail
 * @param apiName       The name of the Microcks API the source of the step is linked to
 * @param apiVersion    The version of that API
 * @param operationName The operation of the step, as Microcks names it in that API
 * @param exampleName   The name of the example of that operation the mocks answer with
 * @author SebastienDegodez
 */
public record WorkflowInjectedFailure(String stepId, String apiName, String apiVersion, String operationName,
      String exampleName) {
}
