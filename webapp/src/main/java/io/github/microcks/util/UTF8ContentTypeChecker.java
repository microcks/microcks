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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

public class UTF8ContentTypeChecker {
   private static final Pattern UTF8_ENCODABLE_PATTERN = Pattern.compile(
         "^(text/[a-z0-9.+-]+|application/([a-z0-9.+-]+\\+)?(json|xml)|application/(javascript|x-www-form-urlencoded))(\\s*;.*)?$",
         Pattern.CASE_INSENSITIVE);

   private UTF8ContentTypeChecker() {
      // Private constructor to prevent instantiation
   }

   public static boolean isUTF8Encodable(String contentType) {
      if (contentType == null)
         return false;
      Matcher matcher = UTF8_ENCODABLE_PATTERN.matcher(contentType.trim());
      return matcher.matches();
   }


   /**
    * Checks if the given byte array is valid UTF-8. Uses incremental decoding with a small buffer to minimize memory
    * allocation while still failing early on invalid input.
    * @param data The byte array to check.
    * @return true if valid UTF-8, false otherwise.
    */
   public static boolean isValidUTF8(byte[] data) {
      CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
      decoder.onMalformedInput(CodingErrorAction.REPORT);
      decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

      ByteBuffer in = ByteBuffer.wrap(data);
      CharBuffer out = CharBuffer.allocate(1024);

      try {
         while (in.hasRemaining()) {
            CoderResult result = decoder.decode(in, out, true);
            if (result.isError()) {
               return false;
            }
            out.clear();
         }
         CoderResult result = decoder.flush(out);
         return !result.isError();
      } catch (Exception e) {
         return false;
      }
   }
}
