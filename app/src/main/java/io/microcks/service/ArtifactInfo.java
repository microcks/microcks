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
package io.github.microcks.service;

/**
 * Simple wrapper for essential information about a API | Service artifact.
 * @author laurent
 */
public class ArtifactInfo {

   private String artifactName;
   private boolean mainArtifact;

   /**
    * Create a new ArtifactInfo with mandatory attributes.
    * @param artifactName The human readable name of artifact
    * @param mainArtifact Is it a main artifact for Service definition or a secondary for examples only?
    */
   public ArtifactInfo(String artifactName, boolean mainArtifact) {
      this.artifactName = artifactName;
      this.mainArtifact = mainArtifact;
   }

   public String getArtifactName() {
      return artifactName;
   }

   public void setArtifactName(String artifactName) {
      this.artifactName = artifactName;
   }

   public boolean isMainArtifact() {
      return mainArtifact;
   }

   public void setMainArtifact(boolean mainArtifact) {
      this.mainArtifact = mainArtifact;
   }
}
