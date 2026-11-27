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
package io.microcks.service;

import io.microcks.domain.ArazzoDocument;
import io.microcks.domain.ArazzoDocumentSummary;
import io.microcks.domain.ArazzoSourceLink;
import io.microcks.domain.ArazzoSourceSummary;
import io.microcks.domain.ArazzoSources;
import io.microcks.domain.ArazzoStepCall;
import io.microcks.domain.ArazzoStepOperation;
import io.microcks.domain.ArazzoWorkflowStatus;
import io.microcks.domain.ArazzoWorkflowSummary;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceRef;
import io.microcks.domain.WorkflowRunSummary;
import io.microcks.repository.ArazzoDocumentRepository;
import io.microcks.repository.ArazzoSourcesRepository;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.artifact.MockRepositoryImportException;
import io.microcks.util.ObjectMapperFactory;
import io.microcks.artifact.arazzo.ArazzoDescription;
import io.microcks.artifact.arazzo.ArazzoDocumentReader;
import io.microcks.artifact.arazzo.ArazzoOperationCall;
import io.microcks.artifact.arazzo.UnsupportedArazzoDocumentException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service bean for common processing around Arazzo documents.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoDocumentService {

   private static final String ARAZZO_SOURCES_KIND = "ArazzoSources";
   private static final String NO_API_NAME_AND_VERSION = "no API name and version for this source";

   private final ArazzoDocumentRepository arazzoDocumentRepository;
   private final ArazzoSourcesRepository arazzoSourcesRepository;
   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;
   private final ArazzoRunHistoryService arazzoRunHistoryService;

   /**
    * Build a new ArazzoDocumentService with its required dependencies.
    * @param arazzoDocumentRepository The repository of imported Arazzo documents
    * @param arazzoSourcesRepository  The repository of imported ArazzoSources overlays
    * @param serviceRepository        The repository of the APIs the source descriptions are linked to
    * @param resourceRepository       The repository of the contracts that define the operations of these APIs
    * @param arazzoRunHistoryService  The service reading the runs history, for the last run of each workflow
    */
   @Inject
   public ArazzoDocumentService(ArazzoDocumentRepository arazzoDocumentRepository,
         ArazzoSourcesRepository arazzoSourcesRepository, ServiceRepository serviceRepository,
         ResourceRepository resourceRepository, ArazzoRunHistoryService arazzoRunHistoryService) {
      this.arazzoDocumentRepository = arazzoDocumentRepository;
      this.arazzoSourcesRepository = arazzoSourcesRepository;
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
      this.arazzoRunHistoryService = arazzoRunHistoryService;
   }

   /**
    * Tell whether an uploaded artifact is an Arazzo document, to import it with this service rather than as an API.
    * @param content The artifact as uploaded
    * @return Whether the artifact is an Arazzo document
    */
   public boolean isArazzoDocument(String content) {
      try {
         return ArazzoDocumentReader.isArazzoDocument(ObjectMapperFactory.getYamlObjectMapper().readTree(content));
      } catch (JsonProcessingException jpe) {
         return false;
      }
   }

   /**
    * Tell whether an uploaded artifact is an ArazzoSources overlay, to import it with this service rather than as an
    * API.
    * @param content The artifact as uploaded
    * @return Whether the artifact is an ArazzoSources overlay
    */
   public boolean isArazzoSources(String content) {
      try {
         return ARAZZO_SOURCES_KIND
               .equals(ObjectMapperFactory.getYamlObjectMapper().readTree(content).path("kind").asText());
      } catch (JsonProcessingException jpe) {
         return false;
      }
   }

   /**
    * Import an Arazzo document so that its workflows are kept in Microcks.
    * @param content The document as uploaded, in YAML or JSON
    * @return The imported document
    * @throws MockRepositoryImportException if the document cannot be imported
    */
   public ArazzoDocument importArazzoDocument(String content) throws MockRepositoryImportException {
      return importArazzoDocument(content, null);
   }

   /**
    * Import an Arazzo document uploaded from a file, so that its workflows are kept in Microcks with that file name.
    * @param content  The document as uploaded, in YAML or JSON
    * @param fileName The name of the file the document was uploaded from; null when it is not known
    * @return The imported document
    * @throws MockRepositoryImportException if the document cannot be imported
    */
   public ArazzoDocument importArazzoDocument(String content, String fileName) throws MockRepositoryImportException {
      JsonNode parsed = parse(content);
      ArazzoDescription description = read(parsed);
      // Re-importing the same title and version updates the existing document.
      ArazzoDocument document = arazzoDocumentRepository.findByTitleAndVersion(description.title(),
            description.version());
      if (document == null) {
         document = new ArazzoDocument();
      }
      document.setTitle(description.title());
      document.setVersion(description.version());
      document.setWorkflowIds(description.workflowIds());
      document.setSourceNames(ArazzoDocumentReader.sourceDescriptionNames(parsed));
      document.setWorkflowCalls(stepCalls(ArazzoDocumentReader.workflowCalls(parsed)));
      document.setArazzoVersion(ArazzoDocumentReader.arazzoVersion(parsed));
      document.setFileName(fileName);
      document.setImportDate(new Date());
      document.setWorkflowDescriptions(ArazzoDocumentReader.workflowDescriptions(parsed));
      document.setContent(content);
      arazzoDocumentRepository.persistOrUpdate(document);
      return document;
   }

   /**
    * List the imported Arazzo documents, with the APIs their source descriptions are linked to and their workflows.
    * @param apiName The name of an API to list only the documents with a source description mapped to it; null to list
    *                every document
    * @return The summary of each listed document
    */
   public List<ArazzoDocumentSummary> getDocumentSummaries(String apiName) {
      List<ArazzoDocumentSummary> summaries = new ArrayList<>();
      for (ArazzoDocument document : arazzoDocumentRepository.findAllSorted()) {
         Map<String, ResolvedSource> sources = resolveSources(document);
         if (apiName == null || mapsToApi(sources, apiName)) {
            summaries.add(summary(document, sources));
         }
      }
      return summaries;
   }

   /** A document stored before workflow descriptions were kept has none. */
   private static String description(ArazzoDocument document, String workflowId) {
      Map<String, String> descriptions = document.getWorkflowDescriptions();
      return descriptions == null ? null : descriptions.get(workflowId);
   }

   /** A document uses an API when its overlay maps one of its sources to it, whether this API exists or not. */
   private static boolean mapsToApi(Map<String, ResolvedSource> sources, String apiName) {
      return sources.values().stream().anyMatch(source -> apiName.equals(source.link().apiName()));
   }

   private ArazzoDocumentSummary summary(ArazzoDocument document, Map<String, ResolvedSource> sources) {
      List<ArazzoSourceSummary> sourceSummaries = sources.values().stream().map(ResolvedSource::summary).toList();
      List<ArazzoWorkflowSummary> workflowSummaries = statuses(document, sources).stream().map(status -> {
         WorkflowRunSummary lastRun = arazzoRunHistoryService.getLastRun(document.getId(), status.workflowId());
         return new ArazzoWorkflowSummary(status, description(document, status.workflowId()),
               document.getWorkflowCalls().get(status.workflowId()).size(), ArazzoRunHistoryService.lastResult(lastRun),
               lastRun);
      }).toList();
      return new ArazzoDocumentSummary(document.getId(), document.getTitle(), document.getVersion(),
            document.getArazzoVersion(), document.getFileName(), document.getImportDate(), sourceSummaries,
            workflowSummaries);
   }

   /**
    * Import an ArazzoSources overlay, that gives the Microcks API name and version of each source description of the
    * Arazzo document with the same title and version.
    * @param content The overlay as uploaded, in YAML or JSON
    * @return The imported overlay
    * @throws MockRepositoryImportException if the overlay cannot be imported
    */
   public ArazzoSources importArazzoSources(String content) throws MockRepositoryImportException {
      JsonNode overlay = parse(content);
      Map<String, ServiceRef> sources = new HashMap<>();
      for (Map.Entry<String, JsonNode> source : overlay.path("sources").properties()) {
         sources.put(source.getKey(), new ServiceRef(null, source.getValue().path("name").asText(),
               source.getValue().path("version").asText()));
      }
      String name = requiredMetadata(overlay, "name");
      String version = requiredMetadata(overlay, "version");
      // Re-importing the same name and version replaces the existing overlay.
      ArazzoSources arazzoSources = arazzoSourcesRepository.findByNameAndVersion(name, version);
      if (arazzoSources == null) {
         arazzoSources = new ArazzoSources();
      }
      arazzoSources.setName(name);
      arazzoSources.setVersion(version);
      arazzoSources.setSources(sources);
      arazzoSourcesRepository.persistOrUpdate(arazzoSources);
      return arazzoSources;
   }

   /** An overlay is matched to its Arazzo document by its metadata name and version: without them, it links nothing. */
   private static String requiredMetadata(JsonNode overlay, String field) throws MockRepositoryImportException {
      String value = overlay.path("metadata").path(field).asText();
      if (value.isBlank()) {
         throw new MockRepositoryImportException("Invalid ArazzoSources overlay: metadata." + field + " is required");
      }
      return value;
   }

   /**
    * Link each source description of an Arazzo document to the Microcks API its overlay gives the name and version of.
    * @param title   The title of the Arazzo document
    * @param version The version of the Arazzo document
    * @return The link of each source description, in the order of the document
    */
   public List<ArazzoSourceLink> getSourceLinks(String title, String version) {
      return resolveSources(arazzoDocumentRepository.findByTitleAndVersion(title, version)).values().stream()
            .map(ResolvedSource::link).toList();
   }

   /**
    * Tell, for each workflow of an Arazzo document, whether it can run against the APIs of Microcks.
    * @param title   The title of the Arazzo document
    * @param version The version of the Arazzo document
    * @return The status of each workflow, in the order of the document
    */
   public List<ArazzoWorkflowStatus> getWorkflowStatuses(String title, String version) {
      ArazzoDocument document = arazzoDocumentRepository.findByTitleAndVersion(title, version);
      return statuses(document, resolveSources(document));
   }

   private static List<ArazzoWorkflowStatus> statuses(ArazzoDocument document, Map<String, ResolvedSource> sources) {
      return document.getWorkflowIds().stream()
            .map(workflowId -> status(workflowId, document.getWorkflowCalls().get(workflowId), sources)).toList();
   }

   /**
    * A workflow is runnable when each of its steps calls an operation found in a linked API; else these are the
    * reasons, in the order of the steps, each given once.
    */
   static ArazzoWorkflowStatus status(String workflowId, List<ArazzoStepCall> calls,
         Map<String, ResolvedSource> sources) {
      Set<String> reasons = new LinkedHashSet<>();
      for (ArazzoStepCall call : calls) {
         String reason = operationOf(call, sources).reason();
         if (reason != null) {
            reasons.add(reason);
         }
      }
      return new ArazzoWorkflowStatus(workflowId, reasons.isEmpty(), List.copyOf(reasons));
   }

   /**
    * @param call    The operation a step calls
    * @param sources The source descriptions of the document, resolved
    * @return The operation found in the linked API, or why it is not found
    */
   static ArazzoStepOperation operationOf(ArazzoStepCall call, Map<String, ResolvedSource> sources) {
      ResolvedSource source = sources.get(call.sourceName());
      ArazzoSourceLink link = source.link();
      String unlinkedReason = unlinkedReason(link);
      if (unlinkedReason != null) {
         return notFound(call, unlinkedReason);
      }
      ArazzoStepOperation operation = source.operations().get(call.operationId());
      if (operation == null) {
         return notFound(call,
               "Operation " + call.operationId() + " not found in " + link.apiName() + " " + link.apiVersion());
      }
      return operation;
   }

   /**
    * @param link The link of a source description
    * @return Why the source is not linked, null when it is linked
    */
   static String unlinkedReason(ArazzoSourceLink link) {
      if (link.apiName() == null) {
         return NO_API_NAME_AND_VERSION;
      }
      if (!link.linked()) {
         return "API " + link.apiName() + " " + link.apiVersion() + " not found";
      }
      return null;
   }

   private static ArazzoStepOperation notFound(ArazzoStepCall call, String reason) {
      return new ArazzoStepOperation(call.sourceName(), call.operationId(), null, null, reason);
   }

   /** Resolve each source description of a document, in the order of the document. */
   Map<String, ResolvedSource> resolveSources(ArazzoDocument document) {
      ArazzoSources overlay = arazzoSourcesRepository.findByNameAndVersion(document.getTitle(), document.getVersion());
      Map<String, ResolvedSource> sources = new LinkedHashMap<>();
      for (String sourceName : document.getSourceNames()) {
         sources.put(sourceName, resolve(sourceName, overlay));
      }
      return sources;
   }

   private ResolvedSource resolve(String sourceName, ArazzoSources overlay) {
      ServiceRef api = overlay == null ? null : overlay.getSources().get(sourceName);
      if (api == null) {
         return new ResolvedSource(new ArazzoSourceLink(sourceName, null, null, false), null, Map.of());
      }
      Service service = serviceRepository.findByNameAndVersion(api.getName(), api.getVersion());
      if (service == null) {
         return new ResolvedSource(new ArazzoSourceLink(sourceName, api.getName(), api.getVersion(), false), null,
               Map.of());
      }
      return new ResolvedSource(new ArazzoSourceLink(sourceName, api.getName(), api.getVersion(), true), service,
            definedOperations(sourceName, service));
   }

   /** An API defines the operations whose operationId appears in the main OpenAPI contract Microcks stored for it. */
   private Map<String, ArazzoStepOperation> definedOperations(String sourceName, Service service) {
      Map<String, ArazzoStepOperation> operations = new HashMap<>();
      for (Resource resource : resourceRepository.findMainByServiceId(service.getId())) {
         if (resource.getType() == ResourceType.OPEN_API_SPEC) {
            operations.putAll(openApiOperations(sourceName, resource.getContent()));
         }
      }
      return operations;
   }

   private static Map<String, ArazzoStepOperation> openApiOperations(String sourceName, String contract) {
      Map<String, ArazzoStepOperation> operations = new HashMap<>();
      for (Map.Entry<String, JsonNode> pathItem : readContract(contract).path("paths").properties()) {
         for (Map.Entry<String, JsonNode> operation : pathItem.getValue().properties()) {
            if (operation.getValue().has("operationId")) {
               String operationId = operation.getValue().path("operationId").asText();
               operations.put(operationId, new ArazzoStepOperation(sourceName, operationId,
                     operation.getKey().toUpperCase(Locale.ROOT), pathItem.getKey(), null));
            }
         }
      }
      return operations;
   }

   private static JsonNode readContract(String contract) {
      try {
         return ObjectMapperFactory.getYamlObjectMapper().readTree(contract);
      } catch (JsonProcessingException jpe) {
         // A contract that cannot be read defines no operation.
         return MissingNode.getInstance();
      }
   }

   private static Map<String, List<ArazzoStepCall>> stepCalls(Map<String, List<ArazzoOperationCall>> workflowCalls) {
      Map<String, List<ArazzoStepCall>> stepCalls = new HashMap<>();
      workflowCalls.forEach((workflowId, calls) -> stepCalls.put(workflowId,
            calls.stream().map(call -> new ArazzoStepCall(call.sourceName(), call.operationId())).toList()));
      return stepCalls;
   }

   /**
    * A source description with its link, the linked API (null when not linked) and the operations the linked API
    * defines, by operationId.
    */
   record ResolvedSource(ArazzoSourceLink link, Service api, Map<String, ArazzoStepOperation> operations) {

      String serviceId() {
         return api == null ? null : api.getId();
      }

      ArazzoSourceSummary summary() {
         return api == null ? new ArazzoSourceSummary(link, null, null)
               : new ArazzoSourceSummary(link, api.getId(), api.getType());
      }
   }

   private static ArazzoDescription read(JsonNode document) throws MockRepositoryImportException {
      try {
         return ArazzoDocumentReader.read(document);
      } catch (UnsupportedArazzoDocumentException uade) {
         throw new MockRepositoryImportException(uade.getMessage(), uade);
      }
   }

   private static JsonNode parse(String content) throws MockRepositoryImportException {
      try {
         return ObjectMapperFactory.getYamlObjectMapper().readTree(content);
      } catch (JsonProcessingException jpe) {
         throw new MockRepositoryImportException("Arazzo document cannot be parsed as YAML or JSON", jpe);
      }
   }
}
