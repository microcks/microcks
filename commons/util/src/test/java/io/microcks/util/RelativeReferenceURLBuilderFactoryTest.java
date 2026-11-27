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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for RelativeReferenceURLBuilderFactory.
 * @author laurent
 */
class RelativeReferenceURLBuilderFactoryTest {

   @Test
   void testGetRelativeReferenceURLBuilder() {
      RelativeReferenceURLBuilder builder = RelativeReferenceURLBuilderFactory.getRelativeReferenceURLBuilder(null);
      assertTrue(builder instanceof SimpleReferenceURLBuilder);

      Map<String, List<String>> properties = Map.of("key", List.of("value1", "value2"));
      builder = RelativeReferenceURLBuilderFactory.getRelativeReferenceURLBuilder(properties);
      assertTrue(builder instanceof SimpleReferenceURLBuilder);

      properties = Map.of(GitLabReferenceURLBuilder.GITLAB_FILE_NAME_HEADER, List.of("value1", "value2"));
      builder = RelativeReferenceURLBuilderFactory.getRelativeReferenceURLBuilder(properties);
      assertTrue(builder instanceof GitLabReferenceURLBuilder);

      properties = Map.of(GitLabReferenceURLBuilder.GITLAB_FILE_NAME_HEADER.toLowerCase(), List.of("value1", "value2"));
      builder = RelativeReferenceURLBuilderFactory.getRelativeReferenceURLBuilder(properties);
      assertTrue(builder instanceof GitLabReferenceURLBuilder);
   }
}
