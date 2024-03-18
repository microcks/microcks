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
import org.springframework.data.annotation.Version;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Domain object representing the result of a microservice test run by Microcks. Test are related to a service and made
 * of multiple test cases corresponding to every operations / actions composing service. Tests are run against a
 * specific endpoint named testedEndpoint. It holds global markers telling if test still ran, is a success, how many
 * times is has taken and so on ...
 * @author laurent
 */
public class TestResult {

   @Id
   private String id;
   @Version
   Long version;
   private Long testNumber;
   private Date testDate;
   private String testedEndpoint;
   private String serviceId;
   private SecretRef secretRef;
   private long timeout;
   private long elapsedTime;
   private boolean success = false;
   private boolean inProgress = true;
   private TestRunnerType runnerType;
   private OperationsHeaders operationsHeaders;
   private OAuth2AuthorizedClient authorizedClient;

   private List<TestCaseResult> testCaseResults = new ArrayList<>();

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public Long getVersion() {
      return version;
   }

   public void setVersion(Long version) {
      this.version = version;
   }

   public Long getTestNumber() {
      return testNumber;
   }

   public void setTestNumber(Long testNumber) {
      this.testNumber = testNumber;
   }

   public Date getTestDate() {
      return testDate;
   }

   public void setTestDate(Date testDate) {
      this.testDate = testDate;
   }

   public String getTestedEndpoint() {
      return testedEndpoint;
   }

   public void setTestedEndpoint(String testedEndpoint) {
      this.testedEndpoint = testedEndpoint;
   }

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
   }

   public SecretRef getSecretRef() {
      return secretRef;
   }

   public void setSecretRef(SecretRef secretRef) {
      this.secretRef = secretRef;
   }

   public long getTimeout() {
      return timeout;
   }

   public void setTimeout(long timeout) {
      this.timeout = timeout;
   }

   public long getElapsedTime() {
      return elapsedTime;
   }

   public void setElapsedTime(long elapsedTime) {
      this.elapsedTime = elapsedTime;
   }

   public boolean isSuccess() {
      return success;
   }

   public void setSuccess(boolean success) {
      this.success = success;
   }

   public boolean isInProgress() {
      return inProgress;
   }

   public void setInProgress(boolean inProgress) {
      this.inProgress = inProgress;
   }

   public TestRunnerType getRunnerType() {
      return runnerType;
   }

   public void setRunnerType(TestRunnerType runnerType) {
      this.runnerType = runnerType;
   }

   public OperationsHeaders getOperationsHeaders() {
      return operationsHeaders;
   }

   public void setOperationsHeaders(OperationsHeaders operationsHeaders) {
      this.operationsHeaders = operationsHeaders;
   }

   public List<TestCaseResult> getTestCaseResults() {
      return testCaseResults;
   }

   public void setTestCaseResults(List<TestCaseResult> testCaseResults) {
      this.testCaseResults = testCaseResults;
   }

   public OAuth2AuthorizedClient getAuthorizedClient() {
      return authorizedClient;
   }

   public void setAuthorizedClient(OAuth2AuthorizedClient authorizedClient) {
      this.authorizedClient = authorizedClient;
   }
}
