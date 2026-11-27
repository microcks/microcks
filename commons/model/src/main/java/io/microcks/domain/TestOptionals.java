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

import java.util.List;

/**
 * Simple bean representing the optional elements of a Test requests. Some of them are made to be persisted into
 * TestResult, some other are just volatile information for execution.
 * @author laurent
 */
public class TestOptionals {

   private SecretRef secretRef;
   private Long timeout;
   private List<String> filteredOperations;
   private OperationsHeaders operationsHeaders;
   private OAuth2ClientContext oAuth2Context;

   public TestOptionals() {
   }

   public TestOptionals(SecretRef secretRef, Long timeout, List<String> filteredOperations,
         OperationsHeaders operationsHeaders, OAuth2ClientContext oAuth2Context) {
      this.secretRef = secretRef;
      this.timeout = timeout;
      this.filteredOperations = filteredOperations;
      this.operationsHeaders = operationsHeaders;
      this.oAuth2Context = oAuth2Context;
   }

   public SecretRef getSecretRef() {
      return secretRef;
   }

   public void setSecretRef(SecretRef secretRef) {
      this.secretRef = secretRef;
   }

   public Long getTimeout() {
      return timeout;
   }

   public void setTimeout(Long timeout) {
      this.timeout = timeout;
   }

   public List<String> getFilteredOperations() {
      return filteredOperations;
   }

   public void setFilteredOperations(List<String> filteredOperations) {
      this.filteredOperations = filteredOperations;
   }

   public OperationsHeaders getOperationsHeaders() {
      return operationsHeaders;
   }

   public void setOperationsHeaders(OperationsHeaders operationsHeaders) {
      this.operationsHeaders = operationsHeaders;
   }

   public OAuth2ClientContext getOAuth2Context() {
      return oAuth2Context;
   }

   public void setOAuth2Context(OAuth2ClientContext oAuth2Context) {
      this.oAuth2Context = oAuth2Context;
   }
}
