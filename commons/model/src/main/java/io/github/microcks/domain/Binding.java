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

/**
 * Binding details for asynchronous operations.
 * @author laurent
 */
public class Binding {

   private BindingType type;
   private String keyType;
   private String destinationType;
   private String destinationName;
   private String method;
   private String qoS;
   private boolean persistent;

   public Binding() {
   }

   public Binding(BindingType type) {
      this.type = type;
   }

   public BindingType getType() {
      return type;
   }

   public void setType(BindingType type) {
      this.type = type;
   }

   public String getKeyType() {
      return keyType;
   }

   public void setKeyType(String keyType) {
      this.keyType = keyType;
   }

   public String getDestinationType() {
      return destinationType;
   }

   public void setDestinationType(String destinationType) {
      this.destinationType = destinationType;
   }

   public String getDestinationName() {
      return destinationName;
   }

   public void setDestinationName(String destinationName) {
      this.destinationName = destinationName;
   }

   public String getMethod() {
      return method;
   }

   public void setMethod(String method) {
      this.method = method;
   }

   public String getQoS() {
      return qoS;
   }

   public void setQoS(String qoS) {
      this.qoS = qoS;
   }

   public boolean isPersistent() {
      return persistent;
   }

   public void setPersistent(boolean persistent) {
      this.persistent = persistent;
   }
}
