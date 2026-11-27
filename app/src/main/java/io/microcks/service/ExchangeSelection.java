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

package io.github.microcks.service;

import java.util.List;
import java.util.Map;

/**
 * Simple wrapper for essential information about an Exchange selection.
 * @author laurent
 */
public class ExchangeSelection {

   private String serviceId;
   private Map<String, List<String>> exchanges;

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceeId) {
      this.serviceId = serviceeId;
   }

   public Map<String, List<String>> getExchanges() {
      return exchanges;
   }

   public void setExchanges(Map<String, List<String>> exchanges) {
      this.exchanges = exchanges;
   }
}
