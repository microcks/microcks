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

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.ArrayList;
import java.util.List;

/**
 * An ArazzoExamples: a secondary artifact, of {@code kind: ArazzoExamples}, recording examples of the workflows of an
 * Arazzo document: the inputs to run a workflow with and what is expected of the run. It is identified by the title and
 * version of that document, which must be imported first. By convention, such a file is named
 * {@code <name>.arazzo-examples.yaml}: Microcks detects it by its kind, not by its extension.
 * @author SebastienDegodez
 */
@MongoEntity(collection = "workflowExamples")
public class WorkflowExamples {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String name;
   private String version;
   private List<WorkflowExample> examples = new ArrayList<>();

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

   /** The recorded examples of the workflows of the document, in the order of the artifact. */
   public List<WorkflowExample> getExamples() {
      return examples;
   }

   public void setExamples(List<WorkflowExample> examples) {
      this.examples = examples;
   }
}
