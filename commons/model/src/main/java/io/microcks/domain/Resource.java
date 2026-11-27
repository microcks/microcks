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
package io.github.microcks.domain;

import org.springframework.data.annotation.Id;

import java.util.HashSet;
import java.util.Set;

/**
 * Domain object representing a Resource: a contractualization companion to microservices Service managed with this
 * application. These are typically retrieved using the serviceId.
 * @author laurent
 */
public class Resource {

   @Id
   private String id;
   private String name;
   private String path;
   private String content;
   private ResourceType type;
   private String serviceId;
   private String sourceArtifact;
   private boolean mainArtifact = false;
   private Set<String> operations;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   public String getContent() {
      return content;
   }

   public void setContent(String content) {
      this.content = content;
   }

   public ResourceType getType() {
      return type;
   }

   public void setType(ResourceType type) {
      this.type = type;
   }

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
   }

   public String getSourceArtifact() {
      return sourceArtifact;
   }

   public void setSourceArtifact(String sourceArtifact) {
      this.sourceArtifact = sourceArtifact;
   }

   public boolean isMainArtifact() {
      return mainArtifact;
   }

   public void setMainArtifact(boolean mainArtifact) {
      this.mainArtifact = mainArtifact;
   }

   public Set<String> getOperations() {
      return operations;
   }

   public void setOperations(Set<String> operations) {
      this.operations = operations;
   }

   public void addOperation(String operation) {
      if (operations == null) {
         operations = new HashSet<>();
      }
      operations.add(operation);
   }
}
