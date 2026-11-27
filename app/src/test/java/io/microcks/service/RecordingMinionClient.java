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
package io.microcks.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands for the Arazzo minion: keeps the runs it is handed, or refuses them when told to.
 * @author SebastienDegodez
 */
public class RecordingMinionClient implements ArazzoMinionClient {

   private final List<ArazzoRunRequest> requests = new ArrayList<>();
   private ArazzoMinionException failure;

   @Override
   public void launch(ArazzoRunRequest request) throws ArazzoMinionException {
      if (failure != null) {
         throw failure;
      }
      requests.add(request);
   }

   /** The runs handed so far. */
   public List<ArazzoRunRequest> requests() {
      return requests;
   }

   /** Every run handed from now on is not received, for this reason. */
   public void failWith(ArazzoMinionException failure) {
      this.failure = failure;
   }
}
