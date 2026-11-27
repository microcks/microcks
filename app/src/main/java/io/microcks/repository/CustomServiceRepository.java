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
package io.github.microcks.repository;

import io.github.microcks.domain.Service;

import java.util.List;
import java.util.Map;

/**
 * Custom repository interface for Service domain objects.
 * @author laurent
 */
public interface CustomServiceRepository {

   List<Service> findByIdIn(List<String> ids);

   List<Service> findByLabels(Map<String, String> labels);

   List<Service> findByLabelsAndNameLike(Map<String, String> labels, String name);

   List<ServiceCount> countServicesByType();

   List<LabelValues> listLabels();

   class ServiceCount {
      String type;
      int number;

      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public int getNumber() {
         return number;
      }

      public void setNumber(int number) {
         this.number = number;
      }
   }

   class LabelValues {
      String key;
      String[] values;

      public String getKey() {
         return key;
      }

      public void setKey(String key) {
         this.key = key;
      }

      public String[] getValues() {
         return values;
      }

      public void setValues(String[] values) {
         this.values = values;
      }
   }
}
