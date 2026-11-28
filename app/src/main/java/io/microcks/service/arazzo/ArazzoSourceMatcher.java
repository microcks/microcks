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
package io.microcks.service.arazzo;

import io.microcks.service.arazzo.view.ArazzoImportAnalysis;
import io.microcks.repository.ServiceRepository;
import io.microcks.util.HTTPDownloader;
import io.microcks.util.ObjectMapperFactory;
import io.microcks.util.ReferenceResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

/**
 * Matches a source description of an Arazzo document to a Microcks API, the way Microcks matches a secondary artifact
 * to the API of its main artifact: the name and version are read in the content of the declared contract, here its
 * {@code info.title} and {@code info.version} as the OpenAPI importer reads them, then looked up exactly. The file name
 * of the declared url plays no role.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoSourceMatcher {

   private static final Set<String> READABLE_SCHEMES = Set.of("http", "https");

   private final ServiceRepository serviceRepository;
   private final ContractDownloader contractDownloader;

   /**
    * Build a new ArazzoSourceMatcher with its required dependencies, that downloads contracts like the artifact
    * download endpoint does.
    * @param serviceRepository The repository of the APIs a source can be matched to
    */
   @Inject
   public ArazzoSourceMatcher(ServiceRepository serviceRepository) {
      this(serviceRepository, url -> HTTPDownloader.handleHTTPDownloadToFile(url, null, true));
   }

   /**
    * Build a new ArazzoSourceMatcher with its required dependencies.
    * @param serviceRepository  The repository of the APIs a source can be matched to
    * @param contractDownloader The downloader of the declared contracts
    */
   ArazzoSourceMatcher(ServiceRepository serviceRepository, ContractDownloader contractDownloader) {
      this.serviceRepository = serviceRepository;
      this.contractDownloader = contractDownloader;
   }

   /** Downloads a declared contract to a local file, that the matcher deletes once read. */
   @FunctionalInterface
   interface ContractDownloader {
      File download(String url) throws IOException;
   }

   /**
    * @param declaredUrl The url a source description declares for its contract. Only an absolute HTTP or HTTPS url can
    *                    be read: a relative one, of an uploaded document, cannot.
    * @return What the declared contract says of the Microcks API to link the source to
    */
   public ArazzoImportAnalysis.Match match(String declaredUrl) {
      return matchContract(readContract(declaredUrl));
   }

   /**
    * Match a source description of an Arazzo document imported from a repository: a relative url is read in that
    * repository, as Microcks reads the relative references of a contract, with the secret of the repository; an
    * absolute one is read as by {@link #match(String)}, without that secret.
    * @param declaredUrl The url a source description declares for its contract
    * @param repository  The repository the document was imported from
    * @return What the declared contract says of the Microcks API to link the source to
    */
   public ArazzoImportAnalysis.Match match(String declaredUrl, ReferenceResolver repository) {
      if (!isRelative(declaredUrl)) {
         return match(declaredUrl);
      }
      try {
         return matchContract(ObjectMapperFactory.getYamlObjectMapper()
               .readTree(repository.getReferenceContent(declaredUrl, StandardCharsets.UTF_8)));
      } catch (IOException ioe) {
         return matchContract(MissingNode.getInstance());
      }
   }

   /**
    * @param declaredUrl The url a source description declares for its contract
    * @param repository  The repository the document was imported from
    * @return The url the contract is read from: a relative url resolved in the repository, else the declared one
    */
   public static String resolve(String declaredUrl, ReferenceResolver repository) {
      return isRelative(declaredUrl) ? repository.getReferenceURL(declaredUrl) : declaredUrl;
   }

   private ArazzoImportAnalysis.Match matchContract(JsonNode contract) {
      JsonNode info = contract.path("info");
      // Read as OpenAPIImporter names the API it imports, so that an unquoted YAML version matches the same way.
      String title = info.path("title").asText();
      String version = info.path("version").asText();
      if (title.isBlank() || version.isBlank()) {
         return new ArazzoImportAnalysis.Match(ArazzoImportAnalysis.Match.Status.UNREADABLE, null, null);
      }
      ArazzoImportAnalysis.Match.Status status = serviceRepository.findByNameAndVersion(title, version) == null
            ? ArazzoImportAnalysis.Match.Status.NO_API
            : ArazzoImportAnalysis.Match.Status.MATCHED;
      return new ArazzoImportAnalysis.Match(status, title, version);
   }

   /** A contract that cannot be read is missing. */
   private JsonNode readContract(String declaredUrl) {
      if (!isReadable(declaredUrl)) {
         return MissingNode.getInstance();
      }
      try {
         File contract = contractDownloader.download(declaredUrl);
         try {
            return ObjectMapperFactory.getYamlObjectMapper().readTree(contract);
         } finally {
            Files.delete(contract.toPath());
         }
      } catch (IOException ioe) {
         return MissingNode.getInstance();
      }
   }

   /** A url without scheme is relative to the document that declares it. */
   private static boolean isRelative(String declaredUrl) {
      try {
         return URI.create(declaredUrl).getScheme() == null;
      } catch (IllegalArgumentException iae) {
         return false;
      }
   }

   private static boolean isReadable(String declaredUrl) {
      try {
         String scheme = URI.create(declaredUrl).getScheme();
         return scheme != null && READABLE_SCHEMES.contains(scheme);
      } catch (IllegalArgumentException iae) {
         return false;
      }
   }
}
