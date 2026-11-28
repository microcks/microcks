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

import io.microcks.domain.Header;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * A call an application made to the Microcks mocks in a verification session, as the mocks received it and answered it.
 * Its order index is given on receipt, from 0: two calls are ordered by their index, never by their date.
 * @author SebastienDegodez
 */
@MongoEntity(collection = "workflowSessionCall")
public class WorkflowSessionRecordedCall {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String sessionId;
   private long index;
   private Date receivedDate;
   private Date expireAt;
   private String serviceName;
   private String serviceVersion;
   private String operationName;
   private String method;
   private String path;
   private String requestUrl;
   private String queryString;
   private Set<Header> requestHeaders = new HashSet<>();
   private String requestBody;
   private int responseStatus;
   private Set<Header> responseHeaders = new HashSet<>();
   private String responseBody;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getSessionId() {
      return sessionId;
   }

   public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }

   /** The place of the call in the order the session received its calls, from 0. */
   public long getIndex() {
      return index;
   }

   public void setIndex(long index) {
      this.index = index;
   }

   public Date getReceivedDate() {
      return receivedDate;
   }

   public void setReceivedDate(Date receivedDate) {
      this.receivedDate = receivedDate;
   }

   public Date getExpireAt() {
      return expireAt;
   }

   public void setExpireAt(Date expireAt) {
      this.expireAt = expireAt;
   }

   /** The name of the Microcks API whose mocks received the call. */
   public String getServiceName() {
      return serviceName;
   }

   public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
   }

   public String getServiceVersion() {
      return serviceVersion;
   }

   public void setServiceVersion(String serviceVersion) {
      this.serviceVersion = serviceVersion;
   }

   /** The name of the operation of that API the mocks dispatched the call to, such as {@code POST /orders}. */
   public String getOperationName() {
      return operationName;
   }

   public void setOperationName(String operationName) {
      this.operationName = operationName;
   }

   public String getMethod() {
      return method;
   }

   public void setMethod(String method) {
      this.method = method;
   }

   /** The path the call was sent to, after the name and version of the API. */
   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   /** The URL the call was sent to, with its query string. */
   public String getRequestUrl() {
      return requestUrl;
   }

   public void setRequestUrl(String requestUrl) {
      this.requestUrl = requestUrl;
   }

   /** The query string of the call, without its ?; null when it has none. */
   public String getQueryString() {
      return queryString;
   }

   public void setQueryString(String queryString) {
      this.queryString = queryString;
   }

   public Set<Header> getRequestHeaders() {
      return requestHeaders;
   }

   public void setRequestHeaders(Set<Header> requestHeaders) {
      this.requestHeaders = requestHeaders;
   }

   public String getRequestBody() {
      return requestBody;
   }

   public void setRequestBody(String requestBody) {
      this.requestBody = requestBody;
   }

   /** The status code the mocks answered with. */
   public int getResponseStatus() {
      return responseStatus;
   }

   public void setResponseStatus(int responseStatus) {
      this.responseStatus = responseStatus;
   }

   public Set<Header> getResponseHeaders() {
      return responseHeaders;
   }

   public void setResponseHeaders(Set<Header> responseHeaders) {
      this.responseHeaders = responseHeaders;
   }

   public String getResponseBody() {
      return responseBody;
   }

   public void setResponseBody(String responseBody) {
      this.responseBody = responseBody;
   }
}
