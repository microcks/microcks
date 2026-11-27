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
package io.github.microcks.web.dto;

import io.github.microcks.domain.OAuth2ClientContext;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer object for grouping base information to launch a test (and thus create a TestResult).
 * @author laurent
 */
public class TestRequestDTO {

   private String serviceId;
   private String testEndpoint;
   private String runnerType;
   private String secretName;
   private Long timeout;
   private List<String> filteredOperations;
   private Map<String, List<HeaderDTO>> operationsHeaders;
   @JsonProperty("oAuth2Context")
   private OAuth2ClientContext oAuth2Context;

   public String getServiceId() {
      return serviceId;
   }

   public String getTestEndpoint() {
      return testEndpoint;
   }

   public String getRunnerType() {
      return runnerType;
   }

   public String getSecretName() {
      return secretName;
   }

   public Long getTimeout() {
      return timeout;
   }

   public List<String> getFilteredOperations() {
      return filteredOperations;
   }

   public Map<String, List<HeaderDTO>> getOperationsHeaders() {
      return operationsHeaders;
   }

   public OAuth2ClientContext getOAuth2Context() {
      return oAuth2Context;
   }

   public void setOAuth2Context(OAuth2ClientContext oAuth2Context) {
      this.oAuth2Context = oAuth2Context;
   }
}
