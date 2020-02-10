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
package io.github.microcks.util.el;

import io.github.microcks.util.el.function.NowELFunction;
import io.github.microcks.util.el.function.RandomIntELFunction;
import io.github.microcks.util.el.function.RandomStringELFunction;

/**
 * Helper class holding commodity methods for getting {@code TemplateEngine} instances..
 * @author laurent
 */
public class TemplateEngineFactory {

   /**
    * Helper method for getting a {@code TemplateEngine} initialized with built-in functions.
    * @return A new TemplateEngine instance.
    */
   public static TemplateEngine getTemplateEngine() {
      TemplateEngine engine = new TemplateEngine();

      // Register some built-in functions into evaluation context.
      engine.getContext().registerFunction("now", NowELFunction.class);
      engine.getContext().registerFunction("randomInt", RandomIntELFunction.class);
      engine.getContext().registerFunction("randomString", RandomStringELFunction.class);

      return engine;
   }
}
