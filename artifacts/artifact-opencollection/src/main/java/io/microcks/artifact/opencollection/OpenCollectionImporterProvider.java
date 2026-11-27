/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.artifact.opencollection;

import io.microcks.artifact.MockRepositoryImporter;
import io.microcks.artifact.MockRepositoryImporterProvider;
import io.microcks.util.ReferenceResolver;

import java.io.IOException;

/**
 * Provider detecting OpenCollection artifacts from their root <code>opencollection</code> property. It is evaluated
 * before every other provider and relies on the raw line, as only a root key counts.
 * @author laurent
 */
public class OpenCollectionImporterProvider implements MockRepositoryImporterProvider {

   /** A RegExp for detecting a line starting with the opencollection root property. */
   public static final String OPENCOLLECTION_REGEXP = "^['\\\"]?opencollection['\\\"]?\\s*:.*";

   /** The UTF-8 byte order mark, as read at the very start of a file. */
   private static final String BYTE_ORDER_MARK = "\uFEFF";

   @Override
   public int priority() {
      return 5;
   }

   @Override
   public boolean accepts(String line) {
      return false;
   }

   @Override
   public boolean accepts(String line, String rawLine) {
      return rootKeyCandidateOf(rawLine).matches(OPENCOLLECTION_REGEXP);
   }

   @Override
   public MockRepositoryImporter create(String path, ReferenceResolver referenceResolver) throws IOException {
      return new OpenCollectionImporter(path);
   }

   /**
    * Only the root key counts: a YAML key must start at column 0, a JSON key is a quoted one (indented when
    * pretty-printed, or right after the opening brace when minified). An indented unquoted key is nested in another
    * structure, so it is not a marker. A leading byte order mark is ignored.
    */
   private static String rootKeyCandidateOf(String lineAsRead) {
      String rawLine = lineAsRead.startsWith(BYTE_ORDER_MARK) ? lineAsRead.substring(BYTE_ORDER_MARK.length())
            : lineAsRead;
      String trimmed = rawLine.trim();
      String afterOpeningBrace = trimmed.startsWith("{") ? trimmed.substring(1).stripLeading() : trimmed;
      return afterOpeningBrace.startsWith("\"") ? afterOpeningBrace : rawLine;
   }
}
