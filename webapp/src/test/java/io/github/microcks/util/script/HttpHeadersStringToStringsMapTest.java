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
package io.github.microcks.util.script;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This is a test case for class HttpHeadersStringToStringsMap class.
 * @author laurent
 */
public class HttpHeadersStringToStringsMapTest {

   @Test
   public void testRFC7230() {
      List<String> value = List.of("12345");
      StringToStringsMap headers = new HttpHeadersStringToStringsMap();
      headers.put("JWTPortail", value);

      assertTrue(headers.hasValues("JWTPortail"));
      assertTrue(headers.hasValues("jwtportail"));
      assertTrue(headers.hasValues("JwTportail"));

      assertEquals(value, headers.get("JWTPortail"));
      assertEquals(value, headers.get("jwtportail"));
      assertEquals(value, headers.get("JwTportail"));
   }
}
