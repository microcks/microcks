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

import io.github.microcks.domain.Secret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper object that can be used to fully resolved references that are placed within a specification or a schema (think
 * of the $ref within OpenAPI or AsyncAPI documents). A resolver is built with a base repository URL (that should point
 * to a "folder") and some security parameters on how to access this repository. It then takes care of retrieving
 * reference content using their relative path from base repository URL.
 * @author laurent
 */
public class ReferenceResolver {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ReferenceResolver.class);

   private String baseRepositoryUrl;
   private Secret repositorySecret;
   private boolean disableSSLValidation;

   private RelativeReferenceURLBuilder urlBuilder;

   private Map<String, File> resolvedReferences = new HashMap<>();
   private Map<String, File> relativeResolvedReferences = new HashMap<>();

   /**
    * Build a new reference resolver.
    * @param baseRepositoryUrl    The root folder representing a remote repository we want to resolved references to
    * @param repositorySecret     An optional Secret containing connection credentials to the repository
    * @param disableSSLValidation Whether to disable or enable the SSL trusting of certificates
    */
   public ReferenceResolver(String baseRepositoryUrl, Secret repositorySecret, boolean disableSSLValidation) {
      this.setBaseRepositoryUrl(baseRepositoryUrl);
      this.repositorySecret = repositorySecret;
      this.disableSSLValidation = disableSSLValidation;
      this.urlBuilder = new SimpleReferenceURLBuilder();
   }

   /**
    * Build a new reference resolver.
    * @param baseRepositoryUrl    The root folder representing a remote repository we want to resolved references to
    * @param repositorySecret     An optional Secret containing connection credentials to the repository
    * @param disableSSLValidation Whether to disable or enable the SSL trusting of certificates
    */
   public ReferenceResolver(String baseRepositoryUrl, Secret repositorySecret, boolean disableSSLValidation,
         RelativeReferenceURLBuilder urlBuilder) {
      this.setBaseRepositoryUrl(baseRepositoryUrl);
      this.repositorySecret = repositorySecret;
      this.disableSSLValidation = disableSSLValidation;
      this.urlBuilder = urlBuilder;
   }

   /** @return the current base repository url. */
   public String getBaseRepositoryUrl() {
      return baseRepositoryUrl;
   }

   /**
    * Update the base repository url to use as root for relative reference resolution.
    * @param baseRepositoryUrl The new base repository url.
    */
   public void setBaseRepositoryUrl(String baseRepositoryUrl) {
      // Remove trailing / to ease things later.
      if (baseRepositoryUrl.endsWith("/")) {
         this.baseRepositoryUrl = baseRepositoryUrl.substring(0, baseRepositoryUrl.length() - 1);
      } else {
         this.baseRepositoryUrl = baseRepositoryUrl;
      }
   }

   /**
    * Get the full URL corresponding to a reference relative path
    * @param referenceRelativePath The reference relative path to resolve
    * @return The absolute URL if a relative path, the orginal path/URL otherwise.
    */
   public String getReferenceURL(String referenceRelativePath) {
      String remoteUrl = referenceRelativePath;
      if (!referenceRelativePath.startsWith("http")) {
         remoteUrl = urlBuilder.buildRemoteURL(this.baseRepositoryUrl, referenceRelativePath);
      }
      return remoteUrl;
   }

   /**
    * Retrieve a reference content from remote repository using its relative path?
    * @param relativePath The relative path of the reference to retrieve
    * @param encoding     The encoding to use for building a string representation of content.
    * @return A string representation of reference content.
    * @throws IOException if access to remote reference fails (not found or connection issues)
    */
   public String getHttpReferenceContent(String relativePath, Charset encoding) throws IOException {
      // Check the file first.
      String remoteUrl = getReferenceURL(relativePath);
      File referenceFile = resolvedReferences.get(remoteUrl);
      if (referenceFile == null) {
         log.info("Downloading a reference file at {}", remoteUrl);
         // Now download this relative file and store its reference into the cache.
         referenceFile = HTTPDownloader.handleHTTPDownloadToFile(remoteUrl, repositorySecret, disableSSLValidation);
         resolvedReferences.put(remoteUrl, referenceFile);
      }
      // Keep track on how we resolved this relativePath.
      relativeResolvedReferences.put(relativePath, referenceFile);

      return Files.readString(referenceFile.toPath(), encoding);
   }

   /**
    * Get resolved references map.
    * @return The map of resolved references with keys being absolute paths.
    */
   public Map<String, File> getResolvedReferences() {
      return resolvedReferences;
   }

   /**
    * Get relative resolved references map.
    * @return The map of resolved references with keys being relative paths.
    */
   public Map<String, File> getRelativeResolvedReferences() {
      return relativeResolvedReferences;
   }

   /** Cleans up already resolved references. */
   public void cleanResolvedReferences() {
      for (File referenceFile : resolvedReferences.values()) {
         referenceFile.delete();
      }
      resolvedReferences.clear();
      relativeResolvedReferences.clear();
   }
}
