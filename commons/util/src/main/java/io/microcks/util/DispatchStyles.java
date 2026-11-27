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

/**
 * Handler for dispatch styles constants.
 * @author laurent
 */
public class DispatchStyles {

   /** Constant for QUERY_MATCH dispatch style. */
   public static final String QUERY_MATCH = "QUERY_MATCH";

   /** Constant for QUERY_ARGS dispatch style. */
   public static final String QUERY_ARGS = "QUERY_ARGS";

   /** Constant for QUERY_HEADER dispatch style. */
   public static final String QUERY_HEADER = "QUERY_HEADER";

   /**
    * Constant for SCRIPT dispatch style.
    * @deprecated Use {@link #GROOVY} instead. Since 1.13.0.
    */
   @Deprecated(since = "Use \"GROOVY\" instead")
   public static final String SCRIPT = "SCRIPT";

   /** Constant for GROOVY dispatch style. */
   public static final String GROOVY = "GROOVY";

   /** Constant for JS dispatch style. */
   public static final String JS = "JS";

   /** Constant for SEQUENCE dispatch style. */
   public static final String SEQUENCE = "SEQUENCE";

   /** Constant for URI_PARAMS dispatch style. */
   public static final String URI_PARAMS = "URI_PARAMS";

   /** Constant for URI_PARTS dispatch style. */
   public static final String URI_PARTS = "URI_PARTS";

   /** Constant for URI_ELEMENTS dispatch style (PARTS and PARAMS). */
   public static final String URI_ELEMENTS = "URI_ELEMENTS";

   /** Constant for JSON_BODY dispatch style. */
   public static final String JSON_BODY = "JSON_BODY";

   /** Constant for PROXY dispatch style. */
   public static final String PROXY = "PROXY";

   /** Constant for FALLBACK dispatch style. */
   public static final String FALLBACK = "FALLBACK";

   /** Constant for PROXY_FALLBACK dispatch style. */
   public static final String PROXY_FALLBACK = "PROXY_FALLBACK";

   /** Constant for RANDOM dispatch style. */
   public static final String RANDOM = "RANDOM";

   private DispatchStyles() {
      // Hide default no argument constructor as it's a utility class.
   }
}
