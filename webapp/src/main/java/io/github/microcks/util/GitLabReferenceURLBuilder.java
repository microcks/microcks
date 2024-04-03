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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * An implementation of <code>RelativeReferenceURLBuilder</code> that follows GitLab conventions for encoding file path
 * into remote URL.
 * @author laurent
 */
public class GitLabReferenceURLBuilder extends SimpleReferenceURLBuilder {

   /** Header used by GitLab APIs to provide the downloaded file name. */
   public static final String GITLAB_FILE_NAME_HEADER = "X-Gitlab-File-Name";

   private static final String REPOSITORY_FILES_MARKER = "/repository/files/";
   private static final String ENCODED_FILE_SEPARATOR = "%2F";

   @Override
   public String getFileName(String baseRepositoryURL, Map<String, List<String>> headers) {
      if (headers != null) {
         for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(GITLAB_FILE_NAME_HEADER)) {
               return headers.get(key).get(0);
            }
         }
      }

      // If not present, extract from raw URL.
      String lastPath = baseRepositoryURL.substring(baseRepositoryURL.lastIndexOf("/") + 1);
      if (lastPath.startsWith("raw?ref=") || lastPath.indexOf('.') == -1) {
         String[] pathElements = baseRepositoryURL.split("/");
         int i = pathElements.length;
         while (i > 0) {
            String path = pathElements[i - 1];
            if (path.contains(".") && path.contains(ENCODED_FILE_SEPARATOR)) {
               return path.substring(path.indexOf(ENCODED_FILE_SEPARATOR) + ENCODED_FILE_SEPARATOR.length());
            }
            i--;
         }
      }
      return null;
   }

   @Override
   public String buildRemoteURL(String baseRepositoryURL, String referencePath) {
      // https://gitlab.com/api/v4/projects/35980862/repository/files/folder%2Fsubfolder%2Ffilename/raw?ref=branch

      // https://gitlab.com/api/v4/projects/35980862/repository/files
      String rootURL = baseRepositoryURL.substring(0,
            baseRepositoryURL.indexOf(REPOSITORY_FILES_MARKER) + REPOSITORY_FILES_MARKER.length() - 1);

      // folder%2Fsubfolder%2Ffilename
      String basePath = baseRepositoryURL.substring(
            baseRepositoryURL.indexOf(REPOSITORY_FILES_MARKER) + REPOSITORY_FILES_MARKER.length(),
            baseRepositoryURL.lastIndexOf("/"));

      // raw?ref=branch
      String formatOptions = baseRepositoryURL.substring(baseRepositoryURL.lastIndexOf("/") + 1);

      // Now do a simple reference url build. We need to ensure that there's a root
      // by adding a starting /. We'll remove it after the build when recomposing result.
      String pathFragment = super.buildRemoteURL("/" + URLDecoder.decode(basePath, StandardCharsets.UTF_8),
            referencePath);

      return rootURL + "/" + URLEncoder.encode(pathFragment.substring(1), StandardCharsets.UTF_8) + "/" + formatOptions;
   }
}
