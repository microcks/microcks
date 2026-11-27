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

import java.util.ArrayList;
import java.util.List;

/**
 * Domain class representing a MicroService and the operations / actions it's holding.
 * @author laurent
 */
public class Service {

   @Id
   private String id;
   private String name;
   private String version;
   private String xmlNS;
   private ServiceType type;
   private Metadata metadata;
   private String sourceArtifact;

   private List<Operation> operations = new ArrayList<>();

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

   public String getVersion() {
      return version;
   }

   public void setVersion(String version) {
      this.version = version;
   }

   public String getXmlNS() {
      return xmlNS;
   }

   public void setXmlNS(String xmlNS) {
      this.xmlNS = xmlNS;
   }

   public ServiceType getType() {
      return type;
   }

   public void setType(ServiceType type) {
      this.type = type;
   }

   public String getSourceArtifact() {
      return sourceArtifact;
   }

   public void setSourceArtifact(String sourceArtifact) {
      this.sourceArtifact = sourceArtifact;
   }

   public List<Operation> getOperations() {
      return operations;
   }

   public void setOperations(List<Operation> operations) {
      this.operations = operations;
   }

   public void addOperation(Operation operation) {
      if (this.operations == null) {
         this.operations = new ArrayList<>();
      }
      operations.add(operation);
   }

   public Metadata getMetadata() {
      return metadata;
   }

   public void setMetadata(Metadata metadata) {
      this.metadata = metadata;
   }
}
