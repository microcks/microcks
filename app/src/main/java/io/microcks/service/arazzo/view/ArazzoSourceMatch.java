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
package io.microcks.service.arazzo.view;

/**
 * What the contract declared by a source description of an Arazzo document says of the Microcks API to link it to: the
 * title and version of the contract are the name and version of that API.
 * @param status          Whether the contract could be read and whether Microcks has an API with its title and version
 * @param contractTitle   The title of the contract; null when it cannot be read
 * @param contractVersion The version of the contract; null when it cannot be read
 * @author SebastienDegodez
 */
public record ArazzoSourceMatch(Status status, String contractTitle, String contractVersion) {

   /** Whether a source description is matched to a Microcks API. */
   public enum Status {
      /** Microcks has an API with the title and version of the declared contract. */
      MATCHED,
      /** Microcks has no API with the title and version of the declared contract. */
      NO_API,
      /** The declared contract cannot be read, or gives no title and version: the API has to be picked. */
      UNREADABLE
   }
}
