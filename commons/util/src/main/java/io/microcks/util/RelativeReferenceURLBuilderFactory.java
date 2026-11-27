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

import java.util.List;
import java.util.Map;

/**
 * Factory for building/retrieving a relative reference url builder.
 * @author laurent
 */
public class RelativeReferenceURLBuilderFactory {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(RelativeReferenceURLBuilderFactory.class);

   /**
    * Create the right RelativeReferenceURLBuilder implementation depending on the base file properties.
    * @param baseFileProperties A map of properties (most of the time headers-like structure)
    * @return A RelativeReferenceURLBuilder that could be used to build URL for relative references in this base file.
    */
   public static RelativeReferenceURLBuilder getRelativeReferenceURLBuilder(
         Map<String, List<String>> baseFileProperties) {
      if (baseFileProperties != null) {
         if (baseFileProperties.containsKey(GitLabReferenceURLBuilder.GITLAB_FILE_NAME_HEADER)
               || baseFileProperties.containsKey(GitLabReferenceURLBuilder.GITLAB_FILE_NAME_HEADER.toLowerCase())) {
            log.debug("Found a GitLab File specific header, returning a GitLabReferenceURLBuilder");
            return new GitLabReferenceURLBuilder();
         }
      }
      log.debug("Returning a SimpleReferenceURLBuilder");
      return new SimpleReferenceURLBuilder();
   }
}
