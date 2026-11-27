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
package io.github.microcks.minion.async;

import io.github.microcks.domain.Secret;
import io.github.microcks.domain.TestRunnerType;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * This is a bean representing the specification of an Asynchronous test using AsyncAPI specification elements.
 * @author laurent
 */
@RegisterForReflection
public class AsyncTestSpecification {

   private TestRunnerType runnerType;
   private String testResultId;
   private String serviceId;
   private String operationName;
   private String endpointUrl;
   private String asyncAPISpec;
   private Secret secret;
   private Long timeoutMS;

   public TestRunnerType getRunnerType() {
      return runnerType;
   }

   public void setRunnerType(TestRunnerType runnerType) {
      this.runnerType = runnerType;
   }

   public String getTestResultId() {
      return testResultId;
   }

   public void setTestResultId(String testResultId) {
      this.testResultId = testResultId;
   }

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
   }

   public String getOperationName() {
      return operationName;
   }

   public void setOperationName(String operationName) {
      this.operationName = operationName;
   }

   public String getEndpointUrl() {
      return endpointUrl;
   }

   public void setEndpointUrl(String endpointUrl) {
      this.endpointUrl = endpointUrl;
   }

   public String getAsyncAPISpec() {
      return asyncAPISpec;
   }

   public void setAsyncAPISpec(String asyncAPISpec) {
      this.asyncAPISpec = asyncAPISpec;
   }

   public Secret getSecret() {
      return secret;
   }

   public void setSecret(Secret secret) {
      this.secret = secret;
   }

   public Long getTimeoutMS() {
      return timeoutMS;
   }

   public void setTimeoutMS(Long timeoutMS) {
      this.timeoutMS = timeoutMS;
   }
}
