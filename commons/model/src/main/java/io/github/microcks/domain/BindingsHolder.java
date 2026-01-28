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

import java.util.Map;

/**
 * Interface for domain objects that can hold protocol bindings.
 * @author adamhicks
 */
public interface BindingsHolder {

   /**
    * Get the bindings map.
    * @return Map of bindings keyed by binding type name
    */
   Map<String, Binding> getBindings();

   /**
    * Add a binding to this holder.
    * @param name    The binding type name
    * @param binding The binding to add
    */
   void addBinding(String name, Binding binding);
}
