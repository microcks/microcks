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

import java.util.Objects;

/**
 * Companion object for Operation that may be used to express constraints on request parameters.
 * @author laurent
 */
public class ParameterConstraint {

   private String name;
   private ParameterLocation in;
   private boolean required;
   private boolean recopy;
   private String mustMatchRegexp;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public ParameterLocation getIn() {
      return in;
   }

   public void setIn(ParameterLocation in) {
      this.in = in;
   }

   public boolean isRequired() {
      return required;
   }

   public void setRequired(boolean required) {
      this.required = required;
   }

   public boolean isRecopy() {
      return recopy;
   }

   public void setRecopy(boolean recopy) {
      this.recopy = recopy;
   }

   public String getMustMatchRegexp() {
      return mustMatchRegexp;
   }

   public void setMustMatchRegexp(String mustMatchRegexp) {
      this.mustMatchRegexp = mustMatchRegexp;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      ParameterConstraint that = (ParameterConstraint) o;
      return Objects.equals(name, that.name) && in == that.in;
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, in);
   }
}
