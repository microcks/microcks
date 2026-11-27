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
package io.github.microcks.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AbsoluteUrlMatcherTest {
   @ParameterizedTest
   @ValueSource(strings = { "https://www.google.com/search?client=firefox-b-e&q=absolute+urls",
         "https://github.com/apoorva256/microcks", "file:///Users/unicorn.jpg" })
   void matches_ShouldReturnTrueForAbsoluteUrls(String absoluteUrl) {
      assertTrue(AbsoluteUrlMatcher.matches(absoluteUrl));
   }

   @ParameterizedTest
   @ValueSource(strings = { "//www.google.com/", "index.html", "../../file.js" })
   void matches_ShouldReturnFalseForNonAbsoluteUrls(String nonAbsoluteUrl) {
      assertFalse(AbsoluteUrlMatcher.matches(nonAbsoluteUrl));
   }
}
