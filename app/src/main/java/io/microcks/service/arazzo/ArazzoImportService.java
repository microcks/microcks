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

import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoDocumentRef;
import io.microcks.service.arazzo.view.ArazzoDocumentSummary;
import io.microcks.service.arazzo.view.ArazzoImportAnalysis;
import io.microcks.service.arazzo.view.ArazzoSourceAnalysis;
import io.microcks.service.arazzo.view.ArazzoSourceLink;
import io.microcks.service.arazzo.view.ArazzoSourceMatch;
import io.microcks.domain.arazzo.ArazzoExamples;
import io.microcks.domain.arazzo.ArazzoMetadata;
import io.microcks.domain.arazzo.ArazzoStepCall;
import io.microcks.service.arazzo.view.ArazzoWorkflowCheck;
import io.microcks.domain.ServiceRef;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.service.arazzo.ArazzoDocumentService.ResolvedSource;
import io.microcks.artifact.MockRepositoryImportException;
import io.microcks.util.ReferenceResolver;
import io.microcks.artifact.arazzo.ArazzoDocumentReader;
import io.microcks.artifact.arazzo.ArazzoStepOrder;
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.panache.common.Sort;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service bean behind the import dialog of Arazzo documents: it analyses a document and the links of its source
 * descriptions before the import, then imports both at once.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoImportService {

   private static final String NOT_AN_ARAZZO_DOCUMENT = "Not an Arazzo document: it declares no arazzo version";

   private final ArazzoDocumentService arazzoDocumentService;
   private final ArazzoSourceMatcher arazzoSourceMatcher;
   private final ServiceRepository serviceRepository;
   private final ArazzoDocumentRepository arazzoDocumentRepository;
   private final ArazzoMetadataRepository arazzoMetadataRepository;

   /**
    * Build a new ArazzoImportService with its required dependencies.
    * @param arazzoDocumentService    The service that imports Arazzo documents and resolves their source descriptions
    * @param arazzoSourceMatcher      The matcher of a source description to the API of its declared contract
    * @param serviceRepository        The repository of the APIs a source can be linked to
    * @param arazzoDocumentRepository The repository of the imported Arazzo documents
    * @param arazzoMetadataRepository  The repository of the ArazzoMetadata that link their sources
    */
   @Inject
   public ArazzoImportService(ArazzoDocumentService arazzoDocumentService, ArazzoSourceMatcher arazzoSourceMatcher,
         ServiceRepository serviceRepository, ArazzoDocumentRepository arazzoDocumentRepository,
         ArazzoMetadataRepository arazzoMetadataRepository) {
      this.arazzoDocumentService = arazzoDocumentService;
      this.arazzoSourceMatcher = arazzoSourceMatcher;
      this.serviceRepository = serviceRepository;
      this.arazzoDocumentRepository = arazzoDocumentRepository;
      this.arazzoMetadataRepository = arazzoMetadataRepository;
   }

   /**
    * Analyse an Arazzo document before importing it: whether it can be imported, the API each source description is
    * matched to, and whether each workflow is ready to run with the links of its sources. Nothing is stored.
    * @param content The document as dropped, in YAML or JSON
    * @param links   The API the developer linked each source description to, by name, a source absent or mapped to null
    *                being not linked; null to link each source to the API it is matched to
    * @return The analysis of the document
    */
   public ArazzoImportAnalysis analyseArazzoDocument(String content, Map<String, ServiceRef> links) {
      Candidate candidate;
      try {
         candidate = read(content);
      } catch (MockRepositoryImportException mrie) {
         return ArazzoImportAnalysis.refused(mrie.getMessage(), apis());
      }
      Map<String, ArazzoSourceMatch> matches = new LinkedHashMap<>();
      candidate.declaredUrls().forEach((sourceName, url) -> matches.put(sourceName, arazzoSourceMatcher.match(url)));
      return analyse(candidate, matches, links == null ? matchedLinks(matches) : links);
   }

   /**
    * Analyse the links of the source descriptions of an imported Arazzo document, to link them again. The contract of a
    * source is read at its declared url, resolved against the url the document was imported from, if any.
    * @param documentId The identifier of the imported document
    * @param links      The API the developer linked each source description to, by name; null for the links of its
    *                   ArazzoMetadata, a source it does not link being linked to the API it is matched to
    * @return The analysis of the document; null when no document has this identifier
    * @throws MockRepositoryImportException if the stored document cannot be read anymore
    */
   public ArazzoImportAnalysis analyseImportedDocument(String documentId, Map<String, ServiceRef> links)
         throws MockRepositoryImportException {
      ArazzoDocument document = arazzoDocumentRepository.findByIdOptional(documentId).orElse(null);
      if (document == null) {
         return null;
      }
      Candidate candidate = new Candidate(document,
            ArazzoDocumentReader.sourceDescriptionUrls(ArazzoDocumentService.parse(document.getContent())));
      Map<String, String> sourceUrls = document.getSourceUrls() == null ? candidate.declaredUrls()
            : document.getSourceUrls();
      Map<String, ArazzoSourceMatch> matches = new LinkedHashMap<>();
      sourceUrls.forEach((sourceName, url) -> matches.put(sourceName, arazzoSourceMatcher.match(url)));
      Map<String, ServiceRef> chosenLinks = links;
      if (chosenLinks == null) {
         chosenLinks = matchedLinks(matches);
         chosenLinks.putAll(storedLinks(document));
      }
      return analyse(candidate, matches, chosenLinks);
   }

   /**
    * Link the source descriptions of an imported Arazzo document again: the links replace the sources of its
    * ArazzoMetadata, nothing else being modified.
    * @param documentId The identifier of the imported document
    * @param links      The API the developer linked each source description to, by name, a source absent or mapped to
    *                   null being not linked
    * @return The summary of the document; null when no document has this identifier
    * @throws MockRepositoryImportException if one of the links is refused: nothing is stored then
    */
   public ArazzoDocumentSummary relinkSources(String documentId, Map<String, ServiceRef> links)
         throws MockRepositoryImportException {
      ArazzoDocument document = arazzoDocumentRepository.findByIdOptional(documentId).orElse(null);
      if (document == null) {
         return null;
      }
      arazzoDocumentService.saveSources(document.getTitle(), document.getVersion(), linkedSources(document, links));
      return arazzoDocumentService.summary(document);
   }

   /**
    * Import an Arazzo document an import job downloaded from a repository, all or nothing. Its source descriptions are
    * matched to the APIs of Microcks, a relative url being read in the repository. When the document has no
    * ArazzoMetadata yet, the matched links are stored as its ArazzoMetadata; an existing one is never replaced.
    * @param content    The document as downloaded, in YAML or JSON
    * @param fileName   The name of the file the document was downloaded from
    * @param repository The repository the document was downloaded from
    * @return The imported document, with the sources it still has to link
    * @throws MockRepositoryImportException if the document is refused: nothing is stored then
    */
   public ArazzoDocumentRef importArazzoDocument(String content, String fileName, ReferenceResolver repository)
         throws MockRepositoryImportException {
      Candidate candidate = read(content);
      ArazzoDocument document = candidate.document();
      Map<String, String> sourceUrls = new LinkedHashMap<>();
      Map<String, ArazzoSourceMatch> matches = new LinkedHashMap<>();
      try {
         candidate.declaredUrls().forEach((sourceName, url) -> {
            sourceUrls.put(sourceName, ArazzoSourceMatcher.resolve(url, repository));
            matches.put(sourceName, arazzoSourceMatcher.match(url, repository));
         });
      } finally {
         repository.cleanResolvedReferences();
      }
      document.setSourceUrls(sourceUrls);
      ArazzoDocument imported = arazzoDocumentService.save(document, fileName);
      Map<String, ServiceRef> matchedLinks = matchedLinks(matches);
      if (!matchedLinks.isEmpty()
            && arazzoMetadataRepository.findByNameAndVersion(imported.getTitle(), imported.getVersion()) == null) {
         arazzoDocumentService.saveSources(imported.getTitle(), imported.getVersion(), matchedLinks);
      }
      return documentRef(imported.getTitle(), imported.getVersion());
   }

   /**
    * Import an ArazzoMetadata an import job downloaded from a repository.
    * @param content The ArazzoMetadata as downloaded, in YAML or JSON
    * @return The document the ArazzoMetadata links the sources of, with the sources it still has to link
    * @throws MockRepositoryImportException if the ArazzoMetadata is refused: nothing is stored then
    */
   public ArazzoDocumentRef importArazzoMetadata(String content) throws MockRepositoryImportException {
      ArazzoMetadata metadata = arazzoDocumentService.importArazzoMetadata(content);
      return documentRef(metadata.getName(), metadata.getVersion());
   }

   /**
    * Import an ArazzoExamples an import job downloaded from a repository.
    * @param content The ArazzoExamples as downloaded, in YAML or JSON
    * @return The document the ArazzoExamples records examples of, with the sources it still has to link
    * @throws MockRepositoryImportException if the ArazzoExamples is refused, its document not being imported for
    *                                       instance: nothing is stored then
    */
   public ArazzoDocumentRef importArazzoExamples(String content) throws MockRepositoryImportException {
      ArazzoExamples examples = arazzoDocumentService.importArazzoExamples(content);
      return documentRef(examples.getName(), examples.getVersion());
   }

   /** The document with this title and version, with its sources linked to no API; no source when not imported. */
   private ArazzoDocumentRef documentRef(String title, String version) {
      ArazzoDocument document = arazzoDocumentRepository.findByTitleAndVersion(title, version);
      if (document == null) {
         return new ArazzoDocumentRef(null, title, version, List.of());
      }
      List<String> unlinkedSources = arazzoDocumentService.getSourceLinks(title, version).stream()
            .filter(link -> !link.linked()).map(ArazzoSourceLink::sourceName).toList();
      return new ArazzoDocumentRef(document.getId(), title, version, unlinkedSources);
   }

   private Map<String, ServiceRef> storedLinks(ArazzoDocument document) {
      ArazzoMetadata metadata = arazzoMetadataRepository.findByNameAndVersion(document.getTitle(),
            document.getVersion());
      return metadata == null ? Map.of() : metadata.getSources();
   }

   private List<ServiceRef> apis() {
      return serviceRepository.listAll(Sort.by("name", "version")).stream()
            .map(api -> new ServiceRef(api.getId(), api.getName(), api.getVersion())).toList();
   }

   private ArazzoImportAnalysis analyse(Candidate candidate, Map<String, ArazzoSourceMatch> matches,
         Map<String, ServiceRef> links) {
      ArazzoDocument document = candidate.document();
      Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document.getSourceNames(), links);
      List<ArazzoStepCall> calls = document.getWorkflowIds().stream()
            .flatMap(workflowId -> document.getWorkflowCalls().get(workflowId).stream()).toList();
      List<ArazzoSourceAnalysis> sourceAnalyses = document.getSourceNames().stream()
            .map(sourceName -> analyse(sources.get(sourceName), candidate.declaredUrls().get(sourceName),
                  matches.get(sourceName), calls, sources))
            .toList();
      List<ArazzoWorkflowCheck> checks = document.getWorkflowIds().stream()
            .map(workflowId -> check(workflowId, document.getWorkflowCalls().get(workflowId), sources)).toList();
      return new ArazzoImportAnalysis(null, document.getArazzoVersion(), document.getTitle(), document.getVersion(),
            document.getWorkflowIds(), sourceAnalyses, checks, apis(), warnings(document));
   }

   /**
    * Arazzo 1.1 (5.8.5.2) intends dependsOn to coordinate asynchronous steps and recommends the order of the list for
    * synchronous ones, without forbidding it: such a document is imported, with a warning per workflow.
    */
   private static List<String> warnings(ArazzoDocument document) {
      JsonNode parsed;
      try {
         parsed = ArazzoDocumentService.parse(document.getContent());
      } catch (MockRepositoryImportException e) {
         // The document was read to build the candidate: its content parses.
         return List.of();
      }
      return document.getWorkflowIds().stream()
            .map(workflowId -> ArazzoWorkflowReader.workflow(parsed, workflowId).map(workflow -> {
               List<String> stepIds = ArazzoStepOrder.dependsOnBetweenSynchronousSteps(workflow);
               return stepIds.isEmpty() ? null
                     : workflowId + ": " + String.join(", ", stepIds) + " declare"
                           + (stepIds.size() == 1 ? "s" : "") + " dependsOn between synchronous steps, while Arazzo"
                           + " 1.1 intends dependsOn to coordinate asynchronous steps";
            }).orElse(null)).filter(Objects::nonNull).toList();
   }

   /**
    * Import an Arazzo document with the links of its source descriptions, stored as its ArazzoMetadata that
    * replaces an existing one. Both are imported, or none.
    * @param content  The document as dropped, in YAML or JSON
    * @param fileName The name of the file the document was dropped from; null when it is not known
    * @param links    The API the developer linked each source description to, by name, a source absent or mapped to
    *                 null being not linked
    * @return The summary of the imported document
    * @throws MockRepositoryImportException if the document or one of its links is refused: nothing is stored then
    */
   public ArazzoDocumentSummary importArazzoDocument(String content, String fileName, Map<String, ServiceRef> links)
         throws MockRepositoryImportException {
      ArazzoDocument document = read(content).document();
      Map<String, ServiceRef> linkedSources = linkedSources(document, links);
      ArazzoDocument imported = arazzoDocumentService.save(document, fileName);
      arazzoDocumentService.saveSources(imported.getTitle(), imported.getVersion(), linkedSources);
      return arazzoDocumentService.summary(imported);
   }

   /** A document read without being stored, with the url each of its source descriptions declares. */
   private record Candidate(ArazzoDocument document, Map<String, String> declaredUrls) {
   }

   private static Candidate read(String content) throws MockRepositoryImportException {
      JsonNode parsed = ArazzoDocumentService.parse(content);
      if (!ArazzoDocumentReader.isArazzoDocument(parsed)) {
         throw new MockRepositoryImportException(NOT_AN_ARAZZO_DOCUMENT);
      }
      return new Candidate(ArazzoDocumentService.document(parsed, content),
            ArazzoDocumentReader.sourceDescriptionUrls(parsed));
   }

   /**
    * The links of the source descriptions of a document that link them to an API, all of them being checked first.
    * @throws MockRepositoryImportException if a link names a source the document does not describe
    */
   private static Map<String, ServiceRef> linkedSources(ArazzoDocument document, Map<String, ServiceRef> links)
         throws MockRepositoryImportException {
      Map<String, ServiceRef> linkedSources = new HashMap<>();
      for (Map.Entry<String, ServiceRef> link : links.entrySet()) {
         if (!document.getSourceNames().contains(link.getKey())) {
            throw new MockRepositoryImportException("Unknown source description: " + link.getKey());
         }
         if (link.getValue() != null) {
            linkedSources.put(link.getKey(), link.getValue());
         }
      }
      return linkedSources;
   }

   /** Each source matched to a Microcks API is linked to it. */
   private static Map<String, ServiceRef> matchedLinks(Map<String, ArazzoSourceMatch> matches) {
      Map<String, ServiceRef> links = new HashMap<>();
      matches.forEach((sourceName, match) -> {
         if (match.status() == ArazzoSourceMatch.Status.MATCHED) {
            links.put(sourceName, new ServiceRef(null, match.contractTitle(), match.contractVersion()));
         }
      });
      return links;
   }

   /**
    * The distinct operations or channels the steps call on a source, how many of them the linked API defines, and how
    * many steps call an operation or a channel of this source that is not found.
    */
   private static ArazzoSourceAnalysis analyse(ResolvedSource source, String declaredUrl, ArazzoSourceMatch match,
         List<ArazzoStepCall> calls, Map<String, ResolvedSource> sources) {
      String sourceName = source.link().sourceName();
      Set<String> operations = new LinkedHashSet<>();
      Set<String> foundOperations = new LinkedHashSet<>();
      int unresolvedSteps = 0;
      for (ArazzoStepCall call : calls) {
         if (call.sourceName().equals(sourceName)) {
            String called = call.channel() == null ? call.operationId() : "#/channels/" + call.channel();
            operations.add(called);
            if (ArazzoDocumentService.operationOf(call, sources).reason() == null) {
               foundOperations.add(called);
            } else {
               unresolvedSteps++;
            }
         }
      }
      return new ArazzoSourceAnalysis(sourceName, declaredUrl, match, source.link(), operations.size(),
            foundOperations.size(), unresolvedSteps);
   }

   /**
    * A workflow is ready to run when each of its steps calls an operation found in a linked API; else it waits for the
    * sources its steps call to be linked, or for the operations not found in the linked APIs.
    */
   private static ArazzoWorkflowCheck check(String workflowId, List<ArazzoStepCall> calls,
         Map<String, ResolvedSource> sources) {
      Set<String> apiNames = new LinkedHashSet<>();
      Set<String> unlinkedSources = new LinkedHashSet<>();
      Set<String> reasons = new LinkedHashSet<>();
      for (ArazzoStepCall call : calls) {
         ResolvedSource source = sources.get(call.sourceName());
         if (ArazzoDocumentService.unlinkedReason(source.link()) != null) {
            unlinkedSources.add(call.sourceName());
         } else {
            apiNames.add(source.link().apiName());
            String reason = ArazzoDocumentService.operationOf(call, sources).reason();
            if (reason != null) {
               reasons.add(reason);
            }
         }
      }
      return new ArazzoWorkflowCheck(workflowId, unlinkedSources.isEmpty() && reasons.isEmpty(), List.copyOf(apiNames),
            List.copyOf(unlinkedSources), List.copyOf(reasons));
   }
}
