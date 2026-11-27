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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for GitLabReferenceURLBuilder.
 * @author laurent
 */
class GitLabReferenceURLBuilderTest {

   private static final String BASE_URL = "https://gitlab.com/api/v4/projects/35980862/repository/files/pastry%2FAPI_Pastry_1.0.0-openapi.yaml/raw?ref=main";

   @Test
   void testGetFileName() {
      GitLabReferenceURLBuilder builder = new GitLabReferenceURLBuilder();

      assertEquals("API_Pastry_1.0.0-openapi.yaml", builder.getFileName(BASE_URL, null));

      Map<String, List<String>> headers = Map.of("X-Gitlab-File-Name", List.of("FooBar.yaml"));
      assertEquals("FooBar.yaml", builder.getFileName(BASE_URL, headers));

      headers = Map.of("x-gitlab-file-name", List.of("FooBar.yaml"));
      assertEquals("FooBar.yaml", builder.getFileName(BASE_URL, headers));
   }

   @Test
   void testBuildRemoteURL() {
      GitLabReferenceURLBuilder builder = new GitLabReferenceURLBuilder();

      assertEquals("https://gitlab.com/api/v4/projects/35980862/repository/files/pastry%2Fschema-ref.yml/raw?ref=main",
            builder.buildRemoteURL(BASE_URL, "schema-ref.yml"));
      assertEquals("https://gitlab.com/api/v4/projects/35980862/repository/files/pastry%2Fschema-ref.yml/raw?ref=main",
            builder.buildRemoteURL(BASE_URL, "./schema-ref.yml"));
      assertEquals("https://gitlab.com/api/v4/projects/35980862/repository/files/refs%2Fschema-ref.yml/raw?ref=main",
            builder.buildRemoteURL(BASE_URL, "../refs/schema-ref.yml"));
   }
}
