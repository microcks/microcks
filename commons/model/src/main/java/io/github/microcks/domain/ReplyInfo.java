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

import java.util.HashMap;
import java.util.Map;

/**
 * Reply information for asynchronous request-reply operations. Captures the runtime expression for determining the
 * reply address and the channel address where replies are sent.
 * @author adamhicks
 */
public class ReplyInfo implements BindingsHolder {

   private String addressLocation;
   private String channelAddress;
   private Map<String, Binding> bindings;

   public ReplyInfo() {
   }

   public String getAddressLocation() {
      return addressLocation;
   }

   public void setAddressLocation(String addressLocation) {
      this.addressLocation = addressLocation;
   }

   public String getChannelAddress() {
      return channelAddress;
   }

   public void setChannelAddress(String channelAddress) {
      this.channelAddress = channelAddress;
   }

   public Map<String, Binding> getBindings() {
      return bindings;
   }

   public void setBindings(Map<String, Binding> bindings) {
      this.bindings = bindings;
   }

   public void addBinding(String name, Binding binding) {
      if (this.bindings == null) {
         this.bindings = new HashMap<>();
      }
      bindings.put(name, binding);
   }
}
