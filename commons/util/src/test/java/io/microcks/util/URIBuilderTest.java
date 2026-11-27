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

import io.github.microcks.domain.Parameter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a Test for URIBuilder class.
 * @laurent
 */
class URIBuilderTest {

   @Test
   void testBuildURIFromPatternWithNoParameters() {
      String pattern = "http://localhost:8080/blog/{year}/{month}";
      try {
         String uri = URIBuilder.buildURIFromPattern(pattern, new ArrayList<Parameter>());
      } catch (NullPointerException npe) {
         fail("buildURIFromPattern should not fail with no parameters");
      }
   }

   @Test
   void testBuildURIFromPattern() {
      // Prepare a bunch of parameters.
      Parameter yearParam = new Parameter();
      yearParam.setName("year");
      yearParam.setValue("2017");

      Parameter monthParam = new Parameter();
      monthParam.setName("month");
      monthParam.setValue("08");

      Parameter statusParam = new Parameter();
      statusParam.setName("status");
      statusParam.setValue("published");

      Parameter pageParam = new Parameter();
      pageParam.setName("page");
      pageParam.setValue("0");

      List<Parameter> parameters = new ArrayList<>();
      parameters.add(yearParam);
      parameters.add(monthParam);
      parameters.add(statusParam);
      parameters.add(pageParam);

      // Test with old wadl like template format.
      String pattern = "http://localhost:8080/blog/{year}/{month}";
      String uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/blog/2017/08?page=0&status=published".equals(uri)
            || "http://localhost:8080/blog/2017/08?status=published&page=0".equals(uri));

      // Test with new swagger like template format.
      pattern = "http://localhost:8080/blog/:year/:month";
      uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/blog/2017/08?page=0&status=published".equals(uri)
            || "http://localhost:8080/blog/2017/08?status=published&page=0".equals(uri));
   }

   @Test
   void testBuildURIFromPatternWithEncoding() {
      // Prepare a bunch of parameters.
      Parameter nameParam = new Parameter();
      nameParam.setName("name");
      nameParam.setValue("Eclair Cafe");

      Parameter descriptionParam = new Parameter();
      descriptionParam.setName("description");
      descriptionParam.setValue("My desc");

      List<Parameter> parameters = new ArrayList<>();
      parameters.add(nameParam);
      parameters.add(descriptionParam);

      // Test with old wadl like template format.
      String pattern = "http://localhost:8080/pastry/{name}";
      String uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/pastry/Eclair%20Cafe?description=My+desc".equals(uri));

      // Test with new swagger like template format.
      pattern = "http://localhost:8080/pastry/:name";
      uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/pastry/Eclair%20Cafe?description=My+desc".equals(uri));
   }

   @Test
   void testBuildURIFromPatternWithMap() {
      // Prepare a bunch of parameters.
      Map<String, String> parameters = new HashMap<>();
      parameters.put("year", "2018");
      parameters.put("month", "05");
      parameters.put("status", "published");
      parameters.put("page", "0");

      // Test with old wadl like template format.
      String pattern = "http://localhost:8080/blog/{year}/{month}";
      String uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/blog/2018/05?page=0&status=published".equals(uri)
            || "http://localhost:8080/blog/2018/05?status=published&page=0".equals(uri));

      // Test with new swagger like template format.
      pattern = "http://localhost:8080/blog/:year/:month";
      uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/blog/2018/05?page=0&status=published".equals(uri)
            || "http://localhost:8080/blog/2018/05?status=published&page=0".equals(uri));
   }

   @Test
   void testBuildURIFromPatternWithMapWithParamsArray() {
      // Prepare a bunch of parameters.
      Multimap<String, String> parameters = ArrayListMultimap.create();
      parameters.put("year", "2018");
      parameters.put("month", "05");
      parameters.put("status", "published");
      parameters.put("status", "proofred");
      parameters.put("page", "0");

      // Test with old wadl like template format.
      String pattern = "http://localhost:8080/blog/{year}/{month}";
      String uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/blog/2018/05?page=0&status=published&status=proofred".equals(uri)
            || "http://localhost:8080/blog/2018/05?status=published&status=proofred&page=0".equals(uri));

      // Test with new swagger like template format.
      pattern = "http://localhost:8080/blog/:year/:month";
      uri = URIBuilder.buildURIFromPattern(pattern, parameters);
      assertTrue("http://localhost:8080/blog/2018/05?page=0&status=published&status=proofred".equals(uri)
            || "http://localhost:8080/blog/2018/05?status=published&status=proofred&page=0".equals(uri));
   }
}
