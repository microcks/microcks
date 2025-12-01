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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling data URIs as defined in RFC 2397. Data URIs are commonly used in OpenAPI specifications to
 * embed binary data inline. Format: "data:[&#60;mediatype&#62;][;base64],&#60;data&#62;".
 *
 * @author microcks
 */
public class DataUriUtil {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(DataUriUtil.class);

   // Pattern to match data URI: data:[<mediatype>][;base64],<data>
   private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:([^;,]+)?(;base64)?,(.*)$", Pattern.DOTALL);

   private DataUriUtil() {
      // Private constructor for utility class
   }

   /**
    * Checks if a string is a data URI.
    * @param uri The string to check
    * @return true if the string is a data URI, false otherwise
    */
   public static boolean isDataUri(String uri) {
      return uri != null && uri.startsWith("data:");
   }

   /**
    * Decodes a data URI and returns the binary data.
    *
    * @param dataUri The data URI to decode
    * @return The decoded binary data
    * @throws IllegalArgumentException if the data URI is malformed
    */
   public static byte[] decodeDataUri(String dataUri) {
      if (!isDataUri(dataUri)) {
         throw new IllegalArgumentException("Not a valid data URI: " + dataUri);
      }

      Matcher matcher = DATA_URI_PATTERN.matcher(dataUri);
      if (!matcher.matches()) {
         throw new IllegalArgumentException("Malformed data URI: " + dataUri);
      }

      String mediaType = matcher.group(1);
      String base64Indicator = matcher.group(2);
      String data = matcher.group(3);

      log.debug("Decoding data URI with mediaType: {}, base64: {}", mediaType, base64Indicator != null);

      if (base64Indicator != null) {
         // Data is base64 encoded
         try {
            return Base64.getDecoder().decode(data);
         } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 encoding in data URI", e);
         }
      } else {
         // Data is URL-encoded or plain text
         // For now, treat as plain text (UTF-8)
         return data.getBytes(StandardCharsets.UTF_8);
      }
   }

   /** Build a data URI from binary content. */
   public static String buildDataUri(byte[] contentBytes) {
      String base64Data = Base64.getEncoder().encodeToString(contentBytes);
      return "data:application/octet-stream;base64," + base64Data;
   }
}
