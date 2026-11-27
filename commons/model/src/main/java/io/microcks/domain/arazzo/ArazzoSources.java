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

import io.microcks.domain.ServiceRef;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An ArazzoSources overlay: a secondary artifact giving the Microcks API name and version of each source description of
 * an Arazzo document, which Arazzo does not carry, and the recorded examples of its workflows. It is identified by the
 * title and version of that document.
 * @author SebastienDegodez
 */
@MongoEntity(collection = "arazzoSources")
public class ArazzoSources {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String name;
   private String version;
   private Map<String, ServiceRef> sources;
   private List<ArazzoWorkflowExample> examples = new ArrayList<>();

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

   public Map<String, ServiceRef> getSources() {
      return sources;
   }

   public void setSources(Map<String, ServiceRef> sources) {
      this.sources = sources;
   }

   /** The recorded examples of the workflows of the document, in the order of the overlay. */
   public List<ArazzoWorkflowExample> getExamples() {
      return examples;
   }

   public void setExamples(List<ArazzoWorkflowExample> examples) {
      this.examples = examples;
   }
}
