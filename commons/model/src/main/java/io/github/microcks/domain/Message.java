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

import java.util.HashSet;
import java.util.Set;

/**
 * Base class holding common attributes for Request, Response and EventMessage domain objects.
 * @author laurent
 */
public abstract class Message {

   private String name;
   private String content;
   private String operationId;
   private String testCaseId;
   private String sourceArtifact;

   private Set<Header> headers;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getContent() {
      return content;
   }

   public void setContent(String content) {
      this.content = content;
   }

   public String getOperationId() {
      return operationId;
   }

   public void setOperationId(String operationId) {
      this.operationId = operationId;
   }

   public String getTestCaseId() {
      return testCaseId;
   }

   public void setTestCaseId(String testCaseId) {
      this.testCaseId = testCaseId;
   }

   public String getSourceArtifact() {
      return sourceArtifact;
   }

   public void setSourceArtifact(String sourceArtifact) {
      this.sourceArtifact = sourceArtifact;
   }

   public Set<Header> getHeaders() {
      return headers;
   }

   public void setHeaders(Set<Header> headers) {
      this.headers = headers;
   }

   public void addHeader(Header header) {
      if (this.headers == null) {
         this.headers = new HashSet<>();
      }
      headers.add(header);
   }
}
