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
package io.microcks.service.arazzo;

/**
 * Hands the runs of Arazzo workflows to the Arazzo minion, which executes them and reports them back.
 * @author SebastienDegodez
 */
public interface ArazzoMinionClient {

   /**
    * @param request The run to execute
    * @throws ArazzoMinionException if the minion does not receive it
    */
   void launch(ArazzoRunRequest request) throws ArazzoMinionException;
}
