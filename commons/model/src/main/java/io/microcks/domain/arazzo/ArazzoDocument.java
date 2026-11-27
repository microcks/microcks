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

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * An Arazzo document imported into Microcks, identified by the title and version of its info object.
 * @author SebastienDegodez
 */
@MongoEntity(collection = "arazzoDocument")
public class ArazzoDocument {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String title;
   private String version;
   private String arazzoVersion;
   private String fileName;
   private Date importDate;
   private Map<String, String> workflowDescriptions;
   private List<String> workflowIds;
   private List<String> sourceNames;
   private Map<String, List<ArazzoStepCall>> workflowCalls;
   private String content;
   private Map<String, String> sourceUrls;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getVersion() {
      return version;
   }

   public void setVersion(String version) {
      this.version = version;
   }

   public String getArazzoVersion() {
      return arazzoVersion;
   }

   public void setArazzoVersion(String arazzoVersion) {
      this.arazzoVersion = arazzoVersion;
   }

   public String getFileName() {
      return fileName;
   }

   public void setFileName(String fileName) {
      this.fileName = fileName;
   }

   public Date getImportDate() {
      return importDate;
   }

   public void setImportDate(Date importDate) {
      this.importDate = importDate;
   }

   public Map<String, String> getWorkflowDescriptions() {
      return workflowDescriptions;
   }

   public void setWorkflowDescriptions(Map<String, String> workflowDescriptions) {
      this.workflowDescriptions = workflowDescriptions;
   }

   public List<String> getWorkflowIds() {
      return workflowIds;
   }

   public void setWorkflowIds(List<String> workflowIds) {
      this.workflowIds = workflowIds;
   }

   public List<String> getSourceNames() {
      return sourceNames;
   }

   public void setSourceNames(List<String> sourceNames) {
      this.sourceNames = sourceNames;
   }

   public Map<String, List<ArazzoStepCall>> getWorkflowCalls() {
      return workflowCalls;
   }

   public void setWorkflowCalls(Map<String, List<ArazzoStepCall>> workflowCalls) {
      this.workflowCalls = workflowCalls;
   }

   public String getContent() {
      return content;
   }

   public void setContent(String content) {
      this.content = content;
   }

   /**
    * @return The url the contract of each source description is read from, by name: its declared url resolved against
    *         the url the document was imported from; null when the document was not imported from a url
    */
   public Map<String, String> getSourceUrls() {
      return sourceUrls;
   }

   public void setSourceUrls(Map<String, String> sourceUrls) {
      this.sourceUrls = sourceUrls;
   }
}
