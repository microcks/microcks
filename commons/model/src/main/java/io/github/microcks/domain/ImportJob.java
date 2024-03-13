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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Domain object representing an import job within Microcks. Import jobs are responsible of periodically checking tests
 * &amp; mocks repository in order to update their definitions with Microcks own repository. They typically used the
 * repositoryUrl attribute, associated with the etag marker in order to easily see if something has been updated.
 * @author laurent
 */
public class ImportJob {

   @Id
   private String id;
   private String name;
   private String repositoryUrl;
   private boolean mainArtifact = true;
   private boolean repositoryDisableSSLValidation = false;
   private String frequency;
   private Date createdDate;
   private Date lastImportDate;
   private String lastImportError;
   private boolean active = false;
   private String etag;

   private Metadata metadata;
   private SecretRef secretRef;
   private Set<ServiceRef> serviceRefs;

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

   public String getRepositoryUrl() {
      return repositoryUrl;
   }

   public void setRepositoryUrl(String repositoryUrl) {
      this.repositoryUrl = repositoryUrl;
   }

   public boolean isMainArtifact() {
      return mainArtifact;
   }

   public void setMainArtifact(boolean mainArtifact) {
      this.mainArtifact = mainArtifact;
   }

   public boolean isRepositoryDisableSSLValidation() {
      return repositoryDisableSSLValidation;
   }

   public void setRepositoryDisableSSLValidation(boolean repositoryDisableSSLValidation) {
      this.repositoryDisableSSLValidation = repositoryDisableSSLValidation;
   }

   public String getFrequency() {
      return frequency;
   }

   public void setFrequency(String frequency) {
      this.frequency = frequency;
   }

   public Date getCreatedDate() {
      return createdDate;
   }

   public void setCreatedDate(Date createdDate) {
      this.createdDate = createdDate;
   }

   public Date getLastImportDate() {
      return lastImportDate;
   }

   public void setLastImportDate(Date lastImportDate) {
      this.lastImportDate = lastImportDate;
   }

   public String getLastImportError() {
      return lastImportError;
   }

   public void setLastImportError(String lastImportError) {
      this.lastImportError = lastImportError;
   }

   public boolean isActive() {
      return active;
   }

   public void setActive(boolean active) {
      this.active = active;
   }

   public String getEtag() {
      return etag;
   }

   public void setEtag(String etag) {
      this.etag = etag;
   }

   public Metadata getMetadata() {
      return metadata;
   }

   public void setMetadata(Metadata metadata) {
      this.metadata = metadata;
   }

   public SecretRef getSecretRef() {
      return secretRef;
   }

   public void setSecretRef(SecretRef secretRef) {
      this.secretRef = secretRef;
   }

   public Set<ServiceRef> getServiceRefs() {
      return serviceRefs;
   }

   public void setServiceRefs(Set<ServiceRef> serviceRefs) {
      this.serviceRefs = serviceRefs;
   }

   public void addServiceRef(ServiceRef serviceRef) {
      if (this.serviceRefs == null) {
         this.serviceRefs = new HashSet<>();
      }
      serviceRefs.add(serviceRef);
   }
}
