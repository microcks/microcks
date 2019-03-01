/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.domain;

import java.util.ArrayList;
import java.util.List;
/**
 * An Operation / action of a micro service. Holds information on
 * messages constitution (inputName, outputName) and how dispatch
 * request to them.
 * @author laurent
 */
public class Operation {

   private String name;
   private String method;
   private String inputName;
   private String outputName;

   private boolean override = false;
   private String dispatcher;
   private String dispatcherRules;
   private Long defaultDelay;

   private List<String> resourcePaths;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getMethod() {
      return method;
   }

   public void setMethod(String method) {
      this.method = method;
   }

   public String getInputName() {
      return inputName;
   }

   public void setInputName(String inputName) {
      this.inputName = inputName;
   }

   public String getOutputName() {
      return outputName;
   }

   public void setOutputName(String outputName) {
      this.outputName = outputName;
   }

   public boolean hasOverride() {
      return this.override;
   }

   public void setOverride(boolean override) {
      this.override = override;
   }

   public String getDispatcher() {
      return dispatcher;
   }

   public void setDispatcher(String dispatcher) {
      this.dispatcher = dispatcher;
   }

   public String getDispatcherRules() {
      return dispatcherRules;
   }

   public void setDispatcherRules(String dispatcherRules) {
      this.dispatcherRules = dispatcherRules;
   }

   public Long getDefaultDelay() {
      return defaultDelay;
   }

   public void setDefaultDelay(Long defaultDelay) {
      this.defaultDelay = defaultDelay;
   }

   public List<String> getResourcePaths() {
      return resourcePaths;
   }

   public void setResourcePaths(List<String> resourcePaths) {
      this.resourcePaths = resourcePaths;
   }

   public void addResourcePath(String resourcePath) {
      if (this.resourcePaths == null) {
         this.resourcePaths = new ArrayList<>();
      }
      resourcePaths.add(resourcePath);
   }
}
