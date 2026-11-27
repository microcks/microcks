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

import java.util.List;
import java.util.Map;

/**
 * A simple implementation of <code>RelativeReferenceURLBuilder</code> that considers that base file name and path are
 * not encoded and are located at the end of remote URL of repository.
 * @author laurent
 */
public class SimpleReferenceURLBuilder implements RelativeReferenceURLBuilder {

   @Override
   public String getFileName(String baseRepositoryURL, Map<String, List<String>> headers) {
      return baseRepositoryURL.substring(baseRepositoryURL.lastIndexOf("/") + 1);
   }

   @Override
   public String buildRemoteURL(String baseRepositoryURL, String referencePath) {
      // Rebuild a downloadable URL to retrieve file.
      String remoteUrl = baseRepositoryURL.substring(0, baseRepositoryURL.lastIndexOf("/"));
      String pathToAppend = referencePath;
      while (pathToAppend.startsWith("../")) {
         remoteUrl = remoteUrl.substring(0, remoteUrl.lastIndexOf("/"));
         pathToAppend = pathToAppend.substring(3);
      }
      if (pathToAppend.startsWith("./")) {
         pathToAppend = pathToAppend.substring(2);
      }
      if (pathToAppend.startsWith("/")) {
         pathToAppend = pathToAppend.substring(1);
      }
      remoteUrl += "/" + pathToAppend;
      return remoteUrl;
   }
}
