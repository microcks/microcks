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
 * Builds remote URL for relative references found into a base file located at specified repository URL. This will allow
 * different implementations depending on where and how the file path is encoded into base repository URL.
 * @author laurent
 */
public interface RelativeReferenceURLBuilder {

   /**
    * Returns the name of the file denoted by <code>baseRepositoryUrl</code>. Addiotional headers (like download
    * headers) can be optionally provided.
    * @param baseRepositoryURL The URL in repository of the file to get name for
    * @param headers           The optional additional file properties or headers
    * @return The name of this file
    */
   String getFileName(String baseRepositoryURL, Map<String, List<String>> headers);

   /**
    * Build a suitable download URL for a reference found into a base file.
    * @param baseRepositoryURL The URL in repository of the file that serves as base file
    * @param referencePath     A path like <code>my-file.yaml</code> or <code>../my-file.yaml</code> to get reference
    *                          from base
    * @return The URL to download reference from
    */
   String buildRemoteURL(String baseRepositoryURL, String referencePath);
}
