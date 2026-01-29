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

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents callback information used for defining callback URLs and related metadata. This class contains details
 * about a callback mechanism such as the URL expression, HTTP method, execution order, and a default delay.
 * @author laurent
 */
public class CallbackInfo implements Serializable {

   @Serial
   private static final long serialVersionUID = 2405172041950251807L;

   private String callbackUrlExpression;
   private String method;
   private Integer order;
   private Long defaultDelay;

   public CallbackInfo() {
   }

   public CallbackInfo(String callbackUrlExpression, String method) {
      this.callbackUrlExpression = callbackUrlExpression;
      this.method = method;
   }

   public String getCallbackUrlExpression() {
      return callbackUrlExpression;
   }

   public void setCallbackUrlExpression(String callbackUrlExpression) {
      this.callbackUrlExpression = callbackUrlExpression;
   }

   public String getMethod() {
      return method;
   }

   public void setMethod(String method) {
      this.method = method;
   }

   public Integer getOrder() {
      return order;
   }

   public void setOrder(Integer order) {
      this.order = order;
   }

   public Long getDefaultDelay() {
      return defaultDelay;
   }

   public void setDefaultDelay(Long defaultDelay) {
      this.defaultDelay = defaultDelay;
   }
}
