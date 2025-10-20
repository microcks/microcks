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
package io.github.microcks.minion.async.producer;

/**
 * A simple enumeration for the different types of AWS credentials provider.
 * @author laurent
 */
public enum AmazonCredentialsProviderType {
   PROFILE("profile"),
   ENV_VARIABLE("env-variable"),
   WEB_IDENTITY("web-identity"),
   POD_IDENTITY("pod-identity");


   private String value;

   AmazonCredentialsProviderType(final String value) {
      this.value = value;
   }

   public String getValue() {
      return value;
   }

   @Override
   public String toString() {
      return this.getValue();
   }
}
