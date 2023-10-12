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
package io.github.microcks.util.el;

import io.github.microcks.util.el.function.*;

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
      engine.getContext().registerFunction("timestamp", NowELFunction.class);
      engine.getContext().registerFunction("uuid", UUIDELFunction.class);
      engine.getContext().registerFunction("guid", UUIDELFunction.class);
      engine.getContext().registerFunction("randomUUID", UUIDELFunction.class);
      engine.getContext().registerFunction("randomInt", RandomIntELFunction.class);
      engine.getContext().registerFunction("randomString", RandomStringELFunction.class);
      engine.getContext().registerFunction("randomBoolean", RandomBooleanELFunction.class);
      engine.getContext().registerFunction("randomValue", RandomValueELFunction.class);

      engine.getContext().registerFunction("randomFirstName", RandomFirstNameELFunction.class);
      engine.getContext().registerFunction("randomLastName", RandomLastNameELFunction.class);
      engine.getContext().registerFunction("randomFullName", RandomFullNameELFunction.class);
      engine.getContext().registerFunction("randomNamePrefix", RandomNamePrefixELFunction.class);
      engine.getContext().registerFunction("randomNameSuffix", RandomNameSuffixELFunction.class);

      engine.getContext().registerFunction("randomCity", RandomCityELFunction.class);
      engine.getContext().registerFunction("randomCountry", RandomCountryELFunction.class);
      engine.getContext().registerFunction("randomCountryCode", RandomCountryCodeELFunction.class);
      engine.getContext().registerFunction("randomStreetName", RandomStreetNameELFunction.class);
      engine.getContext().registerFunction("randomStreetAddress", RandomStreetAddressELFunction.class);
      engine.getContext().registerFunction("randomLatitude", RandomLatitudeELFunction.class);
      engine.getContext().registerFunction("randomLongitude", RandomLongitudeELFunction.class);
      engine.getContext().registerFunction("randomPhoneNumber", RandomPhoneNumberELFunction.class);

      engine.getContext().registerFunction("randomEmail", RandomEmailELFunction.class);

      engine.getContext().registerFunction("put", PutInContextELFunction.class);

      return engine;
   }
}
