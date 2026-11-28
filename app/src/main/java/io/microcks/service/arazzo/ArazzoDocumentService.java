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
import io.microcks.service.arazzo.view.ArazzoDocumentSummary;
import io.microcks.service.arazzo.view.ArazzoSourceLink;
import io.microcks.domain.arazzo.ArazzoExamples;
import io.microcks.service.arazzo.view.ArazzoSourceInfo;
import io.microcks.domain.arazzo.ArazzoMetadata;
import io.microcks.domain.arazzo.ArazzoStepCall;
import io.microcks.service.arazzo.view.ArazzoStepOperation;
import io.microcks.domain.arazzo.ArazzoWorkflowExample;
import io.microcks.service.arazzo.view.ArazzoWorkflowStatus;
import io.microcks.service.arazzo.view.ArazzoWorkflowSummary;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceRef;
import io.microcks.service.arazzo.view.WorkflowRunSummary;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoExamplesRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.artifact.MockRepositoryImportException;
import io.microcks.util.ObjectMapperFactory;
import io.microcks.artifact.arazzo.ArazzoDescription;
import io.microcks.artifact.arazzo.ArazzoDocumentReader;
import io.microcks.artifact.arazzo.ArazzoOperationCall;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;
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
import java.util.Optional;
import java.util.Set;

/**
 * Service bean for common processing around Arazzo documents.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoDocumentService {

   private static final String ARAZZO_METADATA_KIND = "ArazzoMetadata";
   private static final String ARAZZO_EXAMPLES_KIND = "ArazzoExamples";
   private static final String NO_API_NAME_AND_VERSION = "no API name and version for this source";

   private final ArazzoDocumentRepository arazzoDocumentRepository;
   private final ArazzoMetadataRepository arazzoMetadataRepository;
   private final ArazzoExamplesRepository arazzoExamplesRepository;
   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;
   private final ArazzoRunHistoryService arazzoRunHistoryService;

   /**
    * Build a new ArazzoDocumentService with its required dependencies.
    * @param arazzoDocumentRepository The repository of imported Arazzo documents
    * @param arazzoMetadataRepository The repository of imported ArazzoMetadata
    * @param arazzoExamplesRepository The repository of imported ArazzoExamples
    * @param serviceRepository        The repository of the APIs the source descriptions are linked to
    * @param resourceRepository       The repository of the contracts that define the operations of these APIs
    * @param arazzoRunHistoryService  The service reading the runs history, for the last run of each workflow
    */
   @Inject
   public ArazzoDocumentService(ArazzoDocumentRepository arazzoDocumentRepository,
         ArazzoMetadataRepository arazzoMetadataRepository, ArazzoExamplesRepository arazzoExamplesRepository,
         ServiceRepository serviceRepository, ResourceRepository resourceRepository,
         ArazzoRunHistoryService arazzoRunHistoryService) {
      this.arazzoDocumentRepository = arazzoDocumentRepository;
      this.arazzoMetadataRepository = arazzoMetadataRepository;
      this.arazzoExamplesRepository = arazzoExamplesRepository;
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
    * Tell whether an uploaded artifact is an ArazzoMetadata, to import it with this service rather than as an API.
    * @param content The artifact as uploaded
    * @return Whether the artifact is an ArazzoMetadata
    */
   public boolean isArazzoMetadata(String content) {
      return isOfKind(content, ARAZZO_METADATA_KIND);
   }

   /**
    * Tell whether an uploaded artifact is an ArazzoExamples, to import it with this service rather than as an API.
    * @param content The artifact as uploaded
    * @return Whether the artifact is an ArazzoExamples
    */
   public boolean isArazzoExamples(String content) {
      return isOfKind(content, ARAZZO_EXAMPLES_KIND);
   }

   private static boolean isOfKind(String content, String kind) {
      try {
         return kind.equals(ObjectMapperFactory.getYamlObjectMapper().readTree(content).path("kind").asText());
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
      return save(document(parse(content), content), fileName);
   }

   /**
    * Read an Arazzo document without storing it.
    * @param parsed  The document, parsed from YAML or JSON
    * @param content The document as uploaded
    * @return The document, not stored yet
    * @throws MockRepositoryImportException if the document is outside of the subset Microcks supports
    */
   static ArazzoDocument document(JsonNode parsed, String content) throws MockRepositoryImportException {
      ArazzoDescription description = read(parsed);
      ArazzoDocument document = new ArazzoDocument();
      document.setTitle(description.title());
      document.setVersion(description.version());
      document.setWorkflowIds(description.workflowIds());
      document.setSourceNames(ArazzoDocumentReader.sourceDescriptionNames(parsed));
      document.setWorkflowCalls(stepCalls(ArazzoDocumentReader.workflowCalls(parsed)));
      document.setArazzoVersion(ArazzoDocumentReader.arazzoVersion(parsed));
      document.setWorkflowDescriptions(ArazzoDocumentReader.workflowDescriptions(parsed));
      document.setContent(content);
      return document;
   }

   /**
    * Store a document read by {@link #document(JsonNode, String)}.
    * @param document The document to store
    * @param fileName The name of the file the document was uploaded from; null when it is not known
    * @return The stored document
    */
   ArazzoDocument save(ArazzoDocument document, String fileName) {
      // Re-importing the same title and version updates the existing document.
      ArazzoDocument existing = arazzoDocumentRepository.findByTitleAndVersion(document.getTitle(),
            document.getVersion());
      if (existing != null) {
         document.setId(existing.getId());
      }
      document.setFileName(fileName);
      document.setImportDate(new Date());
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

   /**
    * Find an imported Arazzo document by its id, or by its title and version, as the id of a Microcks API can be given.
    * @param documentId The id of the document, or its title and version as {@code <title>:<version>}, a + standing for
    *                   a space in the title
    * @return The document, empty if none
    */
   public Optional<ArazzoDocument> getDocument(String documentId) {
      if (documentId.contains(":")) {
         String title = documentId.substring(0, documentId.indexOf(':')).replace('+', ' ');
         String version = documentId.substring(documentId.indexOf(':') + 1);
         return Optional.ofNullable(arazzoDocumentRepository.findByTitleAndVersion(title, version));
      }
      return arazzoDocumentRepository.findByIdOptional(documentId);
   }

   /** A document stored before workflow descriptions were kept has none. */
   private static String description(ArazzoDocument document, String workflowId) {
      Map<String, String> descriptions = document.getWorkflowDescriptions();
      return descriptions == null ? null : descriptions.get(workflowId);
   }

   /** A document uses an API when its ArazzoMetadata maps one of its sources to it, whether this API exists or not. */
   private static boolean mapsToApi(Map<String, ResolvedSource> sources, String apiName) {
      return sources.values().stream().anyMatch(source -> apiName.equals(source.link().apiName()));
   }

   /**
    * @param document An imported Arazzo document
    * @return Its summary, with the APIs its source descriptions are linked to and its workflows
    */
   ArazzoDocumentSummary summary(ArazzoDocument document) {
      return summary(document, resolveSources(document));
   }

   private ArazzoDocumentSummary summary(ArazzoDocument document, Map<String, ResolvedSource> sources) {
      List<ArazzoSourceInfo> sourceSummaries = sources.values().stream().map(ResolvedSource::summary).toList();
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
    * Import an ArazzoMetadata, that gives the Microcks API name and version of each source description of the Arazzo
    * document with the same title and version. Re-importing the same name and version replaces the existing one.
    * @param content The ArazzoMetadata as uploaded, in YAML or JSON
    * @return The imported ArazzoMetadata
    * @throws MockRepositoryImportException if the ArazzoMetadata cannot be imported
    */
   public ArazzoMetadata importArazzoMetadata(String content) throws MockRepositoryImportException {
      JsonNode artifact = parse(content);
      Map<String, ServiceRef> sources = new HashMap<>();
      for (Map.Entry<String, JsonNode> source : artifact.path("sources").properties()) {
         sources.put(source.getKey(), new ServiceRef(null, source.getValue().path("name").asText(),
               source.getValue().path("version").asText()));
      }
      String name = requiredMetadata(artifact, "name", "ArazzoMetadata");
      String version = requiredMetadata(artifact, "version", "ArazzoMetadata");
      saveSources(name, version, sources);
      return arazzoMetadataRepository.findByNameAndVersion(name, version);
   }

   /**
    * Import an ArazzoExamples, that records examples of the workflows of the Arazzo document with the same title and
    * version, which must be imported first. Re-importing replaces the examples of that document.
    * @param content The ArazzoExamples as uploaded, in YAML or JSON
    * @return The imported ArazzoExamples
    * @throws MockRepositoryImportException if the ArazzoExamples cannot be imported
    */
   public ArazzoExamples importArazzoExamples(String content) throws MockRepositoryImportException {
      JsonNode artifact = parse(content);
      String name = requiredMetadata(artifact, "name", "ArazzoExamples");
      String version = requiredMetadata(artifact, "version", "ArazzoExamples");
      ArazzoDocument document = arazzoDocumentRepository.findByTitleAndVersion(name, version);
      if (document == null) {
         throw new MockRepositoryImportException(
               "Import the Arazzo document " + name + " " + version + " before its examples");
      }
      ArazzoExamples arazzoExamples = arazzoExamplesRepository.findByNameAndVersion(name, version);
      if (arazzoExamples == null) {
         arazzoExamples = new ArazzoExamples();
         arazzoExamples.setName(name);
         arazzoExamples.setVersion(version);
      }
      arazzoExamples.setExamples(ArazzoExamplesReader.read(artifact.path("workflows"), document));
      arazzoExamplesRepository.persistOrUpdate(arazzoExamples);
      return arazzoExamples;
   }

   /**
    * Store the links of the source descriptions of an Arazzo document in its ArazzoMetadata, replacing the existing
    * links.
    * @param name    The title of the Arazzo document
    * @param version The version of the Arazzo document
    * @param sources The Microcks API each linked source description is linked to, by name
    */
   void saveSources(String name, String version, Map<String, ServiceRef> sources) {
      ArazzoMetadata arazzoMetadata = arazzoMetadataRepository.findByNameAndVersion(name, version);
      if (arazzoMetadata == null) {
         arazzoMetadata = new ArazzoMetadata();
         arazzoMetadata.setName(name);
         arazzoMetadata.setVersion(version);
      }
      arazzoMetadata.setSources(sources);
      arazzoMetadataRepository.persistOrUpdate(arazzoMetadata);
   }

   /**
    * @param document   An imported Arazzo document
    * @param workflowId The id of a workflow of that document
    * @return The recorded examples of that workflow, in the order of the ArazzoExamples of the document
    */
   public List<ArazzoWorkflowExample> getExamples(ArazzoDocument document, String workflowId) {
      ArazzoExamples arazzoExamples = arazzoExamplesRepository.findByNameAndVersion(document.getTitle(),
            document.getVersion());
      return arazzoExamples == null ? List.of()
            : arazzoExamples.getExamples().stream().filter(example -> example.workflowId().equals(workflowId))
                  .toList();
   }

   /** An artifact is matched to its Arazzo document by its metadata name and version: without them, it is refused. */
   private static String requiredMetadata(JsonNode artifact, String field, String kind)
         throws MockRepositoryImportException {
      String value = artifact.path("metadata").path(field).asText();
      if (value.isBlank()) {
         throw new MockRepositoryImportException("Invalid " + kind + ": metadata." + field + " is required");
      }
      return value;
   }

   /**
    * Link each source description of an Arazzo document to the Microcks API its ArazzoMetadata gives the name and
    * version of.
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
      JsonNode content = ArazzoWorkflowService.parse(document.getContent());
      return document.getWorkflowIds().stream()
            .map(workflowId -> withAsyncSteps(status(workflowId, document.getWorkflowCalls().get(workflowId), sources),
                  ArazzoWorkflowReader.workflow(content, workflowId).map(ArazzoDocumentService::asyncStepIds)
                        .orElse(List.of())))
            .toList();
   }

   /**
    * @param workflow A workflow of an Arazzo document
    * @return The ids of its steps that send or receive messages, which the Arazzo minion does not run yet
    */
   static List<String> asyncStepIds(ArazzoWorkflow workflow) {
      return workflow.steps().stream().filter(step -> step.messaging() != null).map(ArazzoStep::stepId).toList();
   }

   /**
    * @param asyncStepIds The ids of the steps of a workflow that send or receive messages, at least one
    * @return Why the workflow cannot run until the Arazzo minion runs these steps
    */
   static String asyncStepsReason(List<String> asyncStepIds) {
      return "Async steps cannot be run yet: " + String.join(", ", asyncStepIds);
   }

   /**
    * @param status       Whether a workflow can run against the APIs its sources are linked to, else why
    * @param asyncStepIds The ids of the steps of that workflow that send or receive messages
    * @return The status, not runnable when the workflow has such steps, with that reason last
    */
   static ArazzoWorkflowStatus withAsyncSteps(ArazzoWorkflowStatus status, List<String> asyncStepIds) {
      if (asyncStepIds.isEmpty()) {
         return status;
      }
      List<String> reasons = new ArrayList<>(status.reasons());
      reasons.add(asyncStepsReason(asyncStepIds));
      return new ArazzoWorkflowStatus(status.workflowId(), false, reasons);
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
      if (call.channel() != null) {
         return channelOf(call, source);
      }
      ArazzoStepOperation operation = source.operations().get(call.operationId());
      if (operation == null) {
         return notFound(call,
               "Operation " + call.operationId() + " not found in " + link.apiName() + " " + link.apiVersion());
      }
      return operation;
   }

   /** A step on a channel sends or receives on the address of that channel in the AsyncAPI contract of its API. */
   private static ArazzoStepOperation channelOf(ArazzoStepCall call, ResolvedSource source) {
      String address = source.channels().get(call.channel());
      if (address == null) {
         return notFound(call, "Channel " + call.channel() + " not found in " + source.link().apiName() + " "
               + source.link().apiVersion());
      }
      return new ArazzoStepOperation(call.sourceName(), null, null, null, null, address);
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
      return new ArazzoStepOperation(call.sourceName(), call.operationId(), null, null, reason, call.channel());
   }

   /** Resolve each source description of a document, in the order of the document. */
   Map<String, ResolvedSource> resolveSources(ArazzoDocument document) {
      ArazzoMetadata metadata = arazzoMetadataRepository.findByNameAndVersion(document.getTitle(),
            document.getVersion());
      return resolveSources(document.getSourceNames(), metadata == null ? Map.of() : metadata.getSources());
   }

   /**
    * Resolve source descriptions with the Microcks APIs they are linked to.
    * @param sourceNames The names of the source descriptions, in the order of the document
    * @param links       The Microcks API each linked source description is linked to, by name
    * @return Each source description resolved, in the order of the document
    */
   Map<String, ResolvedSource> resolveSources(List<String> sourceNames, Map<String, ServiceRef> links) {
      Map<String, ResolvedSource> sources = new LinkedHashMap<>();
      for (String sourceName : sourceNames) {
         sources.put(sourceName, resolve(sourceName, links.get(sourceName)));
      }
      return sources;
   }

   private ResolvedSource resolve(String sourceName, ServiceRef api) {
      if (api == null) {
         return new ResolvedSource(new ArazzoSourceLink(sourceName, null, null, false), null, Map.of(), Map.of());
      }
      Service service = serviceRepository.findByNameAndVersion(api.getName(), api.getVersion());
      if (service == null) {
         return new ResolvedSource(new ArazzoSourceLink(sourceName, api.getName(), api.getVersion(), false), null,
               Map.of(), Map.of());
      }
      Map<String, ArazzoStepOperation> operations = new HashMap<>();
      Map<String, String> channels = new HashMap<>();
      for (Resource resource : resourceRepository.findMainByServiceId(service.getId())) {
         if (resource.getType() == ResourceType.OPEN_API_SPEC) {
            operations.putAll(openApiOperations(sourceName, resource.getContent()));
         } else if (resource.getType() == ResourceType.ASYNC_API_SPEC) {
            JsonNode contract = readContract(resource.getContent());
            channels.putAll(asyncApiChannels(contract));
            operations.putAll(asyncApiOperations(sourceName, contract, channels));
         }
      }
      return new ResolvedSource(new ArazzoSourceLink(sourceName, api.getName(), api.getVersion(), true), service,
            operations, channels);
   }

   /**
    * The channels of an AsyncAPI contract, by name, with their address: AsyncAPI 3 gives a channel an address, its name
    * when it does not; AsyncAPI 2 names a channel by its address.
    */
   private static Map<String, String> asyncApiChannels(JsonNode contract) {
      Map<String, String> channels = new HashMap<>();
      for (Map.Entry<String, JsonNode> channel : contract.path("channels").properties()) {
         JsonNode address = channel.getValue().path("address");
         channels.put(channel.getKey(), address.isTextual() ? address.asText() : channel.getKey());
      }
      return channels;
   }

   /**
    * The operations of an AsyncAPI contract, by operationId, named as Microcks names them: by their action and their
    * name in AsyncAPI 3, where the name of an operation is its id; by their verb and their channel in AsyncAPI 2, where
    * a channel holds its publish and subscribe operations.
    */
   private static Map<String, ArazzoStepOperation> asyncApiOperations(String sourceName, JsonNode contract,
         Map<String, String> channels) {
      Map<String, ArazzoStepOperation> operations = new HashMap<>();
      if (contract.path("asyncapi").asText().startsWith("3.")) {
         for (Map.Entry<String, JsonNode> operation : contract.path("operations").properties()) {
            String channelRef = operation.getValue().path("channel").path("$ref").asText();
            operations.put(operation.getKey(),
                  new ArazzoStepOperation(sourceName, operation.getKey(),
                        operation.getValue().path("action").asText().toUpperCase(Locale.ROOT), operation.getKey(), null,
                        channels.get(channelRef.substring(channelRef.lastIndexOf('/') + 1))));
         }
         return operations;
      }
      for (Map.Entry<String, JsonNode> channel : contract.path("channels").properties()) {
         for (Map.Entry<String, JsonNode> operation : channel.getValue().properties()) {
            if (operation.getValue().has("operationId")) {
               String operationId = operation.getValue().path("operationId").asText();
               operations.put(operationId, new ArazzoStepOperation(sourceName, operationId,
                     operation.getKey().toUpperCase(Locale.ROOT), channel.getKey(), null, channel.getKey()));
            }
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
                     operation.getKey().toUpperCase(Locale.ROOT), pathItem.getKey(), null, null));
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
      workflowCalls.forEach((workflowId, calls) -> stepCalls.put(workflowId, calls.stream()
            .map(call -> new ArazzoStepCall(call.sourceName(), call.operationId(), call.channel())).toList()));
      return stepCalls;
   }

   /**
    * A source description with its link, the linked API (null when not linked), the operations the linked API defines,
    * by operationId, and the channels its AsyncAPI contract defines, by name, with their address.
    */
   record ResolvedSource(ArazzoSourceLink link, Service api, Map<String, ArazzoStepOperation> operations,
         Map<String, String> channels) {

      String serviceId() {
         return api == null ? null : api.getId();
      }

      ArazzoSourceInfo summary() {
         return api == null ? new ArazzoSourceInfo(link, null, null)
               : new ArazzoSourceInfo(link, api.getId(), api.getType());
      }
   }

   private static ArazzoDescription read(JsonNode document) throws MockRepositoryImportException {
      try {
         return ArazzoDocumentReader.read(document);
      } catch (UnsupportedArazzoDocumentException uade) {
         throw new MockRepositoryImportException(uade.getMessage(), uade);
      }
   }

   static JsonNode parse(String content) throws MockRepositoryImportException {
      try {
         return ObjectMapperFactory.getYamlObjectMapper().readTree(content);
      } catch (JsonProcessingException jpe) {
         throw new MockRepositoryImportException("Arazzo document cannot be parsed as YAML or JSON", jpe);
      }
   }
}
