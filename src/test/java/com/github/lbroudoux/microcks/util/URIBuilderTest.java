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
package com.github.lbroudoux.microcks.util;

import com.github.lbroudoux.microcks.domain.Parameter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * This is a Test for URIBuidler class
 * @laurent
 */
public class URIBuilderTest {

   @Test
   public void testBuildURIFromPattern() {
      // Prepare a bundh of parameters.
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
}
