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
import io.microcks.domain.arazzo.ArazzoExampleLaunch;
import io.microcks.domain.arazzo.ArazzoStepOperation;
import io.microcks.domain.arazzo.ArazzoWorkflowExample;
import io.microcks.domain.arazzo.ArazzoWorkflowStatus;
import io.microcks.domain.Request;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Response;
import io.microcks.domain.Secret;
import io.microcks.domain.SecretRef;
import io.microcks.domain.arazzo.WorkflowExampleCheck;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.domain.arazzo.WorkflowRunOrigin;
import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowRunTarget;
import io.microcks.domain.arazzo.WorkflowSourceTarget;
import io.microcks.domain.arazzo.WorkflowStepResult;
import io.microcks.repository.RequestRepository;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.SecretRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.service.arazzo.ArazzoDocumentService.ResolvedSource;
import io.microcks.artifact.arazzo.ArazzoDataFlow;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service bean running the workflows of Arazzo documents: it records each run, hands it to the Arazzo minion, and
 * records what the minion reports back.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoRunService {

   /** How long a step waits for its response when the run does not tell. */
   static final long DEFAULT_TIMEOUT = 10000L;

   /** An absolute http or https URL: a scheme, then a host. */
   private static final Pattern ABSOLUTE_HTTP_URL = Pattern.compile("(?i)https?://[^/?#\\s]+\\S*");

   private final ArazzoDocumentService arazzoDocumentService;
   private final WorkflowResultRepository workflowResultRepository;
   private final RequestRepository requestRepository;
   private final ResponseRepository responseRepository;
   private final ArazzoMinionClient arazzoMinionClient;
   private final SecretRepository secretRepository;
   private final ArazzoMockCoherenceService arazzoMockCoherenceService;
   private final ResourceRepository resourceRepository;

   /** The URL the relative references of the stored contracts resolve against, as for OpenAPI contract tests. */
   @ConfigProperty(name = "validation.resourceUrl", defaultValue = "http://localhost:8080/api/resources/")
   String validationResourceUrl;

   /**
    * Build a new ArazzoRunService with its required dependencies.
    * @param arazzoDocumentService    The service finding the Arazzo documents and linking their source descriptions to
    *                                 Microcks APIs
    * @param workflowResultRepository The repository of the runs
    * @param requestRepository        The repository the requests of the steps are stored in
    * @param responseRepository       The repository the responses of the steps are stored in
    * @param arazzoMinionClient       The client of the Arazzo minion that executes the runs
    * @param secretRepository         The repository of the secrets whose authorization the calls of a run carry
    * @param arazzoMockCoherence      The service telling which example the mocks lack when a step fails
    * @param resourceRepository       The repository of the OpenAPI contracts the responses are validated against
    */
   @Inject
   public ArazzoRunService(ArazzoDocumentService arazzoDocumentService,
         WorkflowResultRepository workflowResultRepository, RequestRepository requestRepository,
         ResponseRepository responseRepository, ArazzoMinionClient arazzoMinionClient,
         SecretRepository secretRepository, ArazzoMockCoherenceService arazzoMockCoherence,
         ResourceRepository resourceRepository) {
      this.arazzoDocumentService = arazzoDocumentService;
      this.workflowResultRepository = workflowResultRepository;
      this.requestRepository = requestRepository;
      this.responseRepository = responseRepository;
      this.arazzoMinionClient = arazzoMinionClient;
      this.secretRepository = secretRepository;
      this.arazzoMockCoherenceService = arazzoMockCoherence;
      this.resourceRepository = resourceRepository;
   }

   /**
    * Run a workflow against the Microcks mocks of the APIs its source descriptions are linked to.
    * @param documentId The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId The id of the workflow in that document
    * @param inputs     The inputs of the workflow, by name; null for none
    * @param timeout    How long a step waits for its response, in milliseconds; null for 10 seconds
    * @return The run, in progress; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown or cannot run, or if an input it needs is missing
    */
   public Optional<WorkflowResult> launchRun(String documentId, String workflowId, Map<String, Object> inputs,
         Long timeout) throws ArazzoRunRefusedException {
      return launchRun(documentId, workflowId, inputs, timeout, null, false);
   }

   /**
    * Run a workflow against the implementations of the APIs its source descriptions are linked to, against their
    * Microcks mocks, or against the mocks of some of them and the implementations of the others (mixed run).
    * @param documentId      The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId      The id of the workflow in that document
    * @param inputs          The inputs of the workflow, by name
    * @param timeout         How long a step waits for its response, in milliseconds; null for 10 seconds
    * @param implementations The base URL of each source sent to its implementation, the secret of the calls to the
    *                        implementations, and where each source goes in a mixed run; null for the mocks
    * @return The run, in progress; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown or cannot run, or cannot reach the implementations
    */
   public Optional<WorkflowResult> launchRun(String documentId, String workflowId, Map<String, Object> inputs,
         Long timeout, ArazzoRunImplementations implementations) throws ArazzoRunRefusedException {
      return launchRun(documentId, workflowId, inputs, timeout, implementations, false);
   }

   /**
    * Run a workflow against the Microcks mocks of the APIs its source descriptions are linked to.
    * @param documentId     The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId     The id of the workflow in that document
    * @param inputs         The inputs of the workflow, by name
    * @param timeout        How long a step waits for its response, in milliseconds; null for 10 seconds
    * @param validateSchema Whether each response is validated against the OpenAPI schema of its operation
    * @return The run, in progress; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown or cannot run
    */
   public Optional<WorkflowResult> launchRun(String documentId, String workflowId, Map<String, Object> inputs,
         Long timeout, boolean validateSchema) throws ArazzoRunRefusedException {
      return launchRun(documentId, workflowId, inputs, timeout, null, validateSchema);
   }

   /**
    * Run a workflow against the implementations of the APIs its source descriptions are linked to, or against their
    * Microcks mocks.
    * @param documentId      The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId      The id of the workflow in that document
    * @param inputs          The inputs of the workflow, by name; null for none
    * @param timeout         How long a step waits for its response, in milliseconds; null for 10 seconds
    * @param implementations The base URL of each linked source and the secret of the calls; null for the mocks
    * @param validateSchema  Whether each response is validated against the OpenAPI schema of its operation
    * @return The run, in progress; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown or cannot run, if an input it needs is missing, or if
    *                                   it cannot reach the implementations
    */
   public Optional<WorkflowResult> launchRun(String documentId, String workflowId, Map<String, Object> inputs,
         Long timeout, ArazzoRunImplementations implementations, boolean validateSchema)
         throws ArazzoRunRefusedException {
      Optional<ArazzoDocument> found = documentWith(documentId, workflowId);
      if (found.isEmpty()) {
         return Optional.empty();
      }
      return Optional.of(launch(found.get(), workflowId, inputs, timeout, implementations, validateSchema, null));
   }

   /**
    * Run a workflow against the Microcks mocks for an application that posted its inputs to the Mock URL of the
    * workflow; the run keeps the Mock URL as its origin.
    * @param documentId The id of the Arazzo document
    * @param workflowId The id of the workflow in that document
    * @param inputs     The inputs of the workflow, by name
    * @return The run, in progress; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown or cannot run, or if an input it needs is missing
    */
   public Optional<WorkflowResult> launchMockUrlRun(String documentId, String workflowId, Map<String, Object> inputs)
         throws ArazzoRunRefusedException {
      Optional<ArazzoDocument> found = documentWith(documentId, workflowId);
      if (found.isEmpty()) {
         return Optional.empty();
      }
      return Optional.of(launch(found.get(), workflowId, inputs, null, null, false, null, WorkflowRunOrigin.MOCK_URL));
   }

   /**
    * Play a recorded example of a workflow: run the workflow with the inputs of the example, then judge whether the run
    * matches what the example expects.
    * @param documentId      The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId      The id of the workflow in that document
    * @param exampleName     The name of the recorded example of that workflow
    * @param timeout         How long a step waits for its response, in milliseconds; null for 10 seconds
    * @param implementations The base URL of each linked source and the secret of the calls; null for the mocks
    * @param validateSchema  Whether each response is validated against the OpenAPI schema of its operation
    * @return The run, in progress; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown, has no such example, cannot run, if an input it
    *                                   needs is missing from the example, or if it cannot reach the implementations
    */
   public Optional<WorkflowResult> launchExampleRun(String documentId, String workflowId, String exampleName,
         Long timeout, ArazzoRunImplementations implementations, boolean validateSchema)
         throws ArazzoRunRefusedException {
      Optional<ArazzoDocument> found = documentWith(documentId, workflowId);
      if (found.isEmpty()) {
         return Optional.empty();
      }
      ArazzoWorkflowExample example = arazzoDocumentService.getExamples(found.get(), workflowId).stream()
            .filter(candidate -> candidate.name().equals(exampleName)).findFirst().orElseThrow(
                  () -> new ArazzoRunRefusedException("Workflow " + workflowId + " has no example " + exampleName));
      return Optional.of(launch(found.get(), workflowId, example.inputs(), timeout, implementations, validateSchema,
            ArazzoExampleJudge.expecting(example)));
   }

   /**
    * Play every recorded example of a workflow, one run per example: an example that cannot run is refused with its
    * reason while the others run.
    * @param documentId      The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId      The id of the workflow in that document
    * @param timeout         How long a step waits for its response, in milliseconds; null for 10 seconds
    * @param implementations The base URL of each linked source and the secret of the calls; null for the mocks
    * @param validateSchema  Whether each response is validated against the OpenAPI schema of its operation
    * @return How the run of each example was launched, in the order of the examples; empty if the document does not
    *         exist
    * @throws ArazzoRunRefusedException if the workflow is unknown
    */
   public Optional<List<ArazzoExampleLaunch>> launchExampleRuns(String documentId, String workflowId, Long timeout,
         ArazzoRunImplementations implementations, boolean validateSchema) throws ArazzoRunRefusedException {
      Optional<ArazzoDocument> found = documentWith(documentId, workflowId);
      if (found.isEmpty()) {
         return Optional.empty();
      }
      List<ArazzoExampleLaunch> launches = new ArrayList<>();
      for (ArazzoWorkflowExample example : arazzoDocumentService.getExamples(found.get(), workflowId)) {
         try {
            WorkflowResult run = launch(found.get(), workflowId, example.inputs(), timeout, implementations,
                  validateSchema, ArazzoExampleJudge.expecting(example));
            launches.add(new ArazzoExampleLaunch(example.name(), run.getId(), run.getNumber(), null));
         } catch (ArazzoRunRefusedException arre) {
            launches.add(new ArazzoExampleLaunch(example.name(), null, null, arre.getMessage()));
         }
      }
      return Optional.of(launches);
   }

   /** The Arazzo document, empty if it does not exist; refused when it has no such workflow. */
   private Optional<ArazzoDocument> documentWith(String documentId, String workflowId)
         throws ArazzoRunRefusedException {
      Optional<ArazzoDocument> found = arazzoDocumentService.getDocument(documentId);
      if (found.isPresent() && !found.get().getWorkflowIds().contains(workflowId)) {
         throw new ArazzoRunRefusedException("Unknown workflow: " + workflowId);
      }
      return found;
   }

   private WorkflowResult launch(ArazzoDocument document, String workflowId, Map<String, Object> inputs, Long timeout,
         ArazzoRunImplementations implementations, boolean validateSchema, WorkflowExampleCheck example)
         throws ArazzoRunRefusedException {
      return launch(document, workflowId, inputs, timeout, implementations, validateSchema, example, null);
   }

   private WorkflowResult launch(ArazzoDocument document, String workflowId, Map<String, Object> inputs, Long timeout,
         ArazzoRunImplementations implementations, boolean validateSchema, WorkflowExampleCheck example,
         WorkflowRunOrigin origin) throws ArazzoRunRefusedException {
      List<String> asyncStepIds = workflowOf(document, workflowId).map(ArazzoDocumentService::asyncStepIds)
            .orElse(List.of());
      if (!asyncStepIds.isEmpty()) {
         throw new ArazzoRunRefusedException(ArazzoDocumentService.asyncStepsReason(asyncStepIds));
      }
      Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document);
      ArazzoWorkflowStatus status = ArazzoDocumentService.status(workflowId,
            document.getWorkflowCalls().get(workflowId), sources);
      if (!status.runnable()) {
         throw new ArazzoRunRefusedException(
               "Workflow " + workflowId + " cannot run: " + String.join(", ", status.reasons()));
      }
      Map<String, Object> givenInputs = inputs == null ? Map.of() : inputs;
      List<String> missingInputs = missingInputs(document, workflowId, givenInputs);
      if (!missingInputs.isEmpty()) {
         throw new ArazzoRunRefusedException(
               (missingInputs.size() == 1 ? "Missing input: " : "Missing inputs: ") + String.join(", ", missingInputs));
      }
      List<ResolvedSource> linked = sources.values().stream().filter(source -> source.api() != null).toList();
      Map<String, String> baseUrls = baseUrlsOf(document, workflowId, linked, implementations);
      // The secret is for the implementations: a run sending no source to its implementation neither needs nor keeps it.
      boolean callsAnImplementation = linked.stream()
            .anyMatch(source -> !onMocks(source.link().sourceName(), implementations));
      Secret secret = callsAnImplementation ? secretOf(implementations) : null;
      WorkflowResult run = new WorkflowResult();
      run.setDocumentId(document.getId());
      run.setDocumentTitle(document.getTitle());
      run.setDocumentVersion(document.getVersion());
      run.setWorkflowId(workflowId);
      run.setTarget(targetOf(implementations));
      run.setSources(linked.stream()
            .map(source -> new WorkflowSourceTarget(source.link().sourceName(), source.serviceId(),
                  source.link().apiName(), source.link().apiVersion(), baseUrls.get(source.link().sourceName())))
            .toList());
      run.setSecretRef(secret == null ? null : new SecretRef(secret.getId(), secret.getName()));
      run.setInputs(givenInputs);
      run.setTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout);
      run.setStatus(WorkflowRunStatus.IN_PROGRESS);
      run.setStartedDate(new Date());
      run.setNumber(nextNumber(document.getId()));
      run.setValidateSchema(validateSchema);
      run.setExample(example);
      run.setOrigin(origin);
      WorkflowResult launched = workflowResultRepository.save(run);
      try {
         arazzoMinionClient.launch(new ArazzoRunRequest(launched.getId(), workflowId, document.getContent(),
               givenInputs, targets(linked, baseUrls, validateSchema), launched.getTimeout(), authorizationOf(secret),
               validateSchema));
      } catch (ArazzoMinionException ame) {
         launched.setStatus(WorkflowRunStatus.FAILURE);
         launched.setMessage(ame.getMessage());
         judgeExample(launched);
         workflowResultRepository.save(launched);
      }
      return launched;
   }

   /**
    * The inputs a run of the workflow needs that are not given, or given as null; in the order the kernel gives them.
    */
   private static List<String> missingInputs(ArazzoDocument document, String workflowId,
         Map<String, Object> givenInputs) {
      return workflowOf(document, workflowId).map(ArazzoDataFlow::neededInputs).orElse(List.of()).stream()
            .filter(inputName -> givenInputs.get(inputName) == null).toList();
   }

   private static Optional<ArazzoWorkflow> workflowOf(ArazzoDocument document, String workflowId) {
      return ArazzoWorkflowReader.workflow(ArazzoWorkflowService.parse(document.getContent()), workflowId);
   }

   /**
    * Runs are numbered per document, from 1, independently of the tests of the APIs they call; the verifications of
    * sessions share this numbering, as they join the runs history.
    */
   long nextNumber(String documentId) {
      WorkflowResult last = workflowResultRepository.findTopByDocumentIdOrderByNumberDesc(documentId);
      return last == null || last.getNumber() == null ? 1L : last.getNumber() + 1L;
   }

   /** The base URL of the Microcks mocks of the API a source is linked to, as Microcks encodes it. */
   private static String mocksOf(ResolvedSource source) {
      return "/rest/" + URLEncoder.encode(source.link().apiName(), StandardCharsets.UTF_8) + "/"
            + URLEncoder.encode(source.link().apiVersion(), StandardCharsets.UTF_8);
   }

   /** Where each linked source is called: its mocks, or the base URL of its implementation. */
   private static Map<String, String> baseUrlsOf(ArazzoDocument document, String workflowId,
         List<ResolvedSource> linked, ArazzoRunImplementations implementations) throws ArazzoRunRefusedException {
      if (implementations != null && implementations.mixed()) {
         requireClearTargets(document, workflowId, linked, implementations);
      }
      Map<String, String> baseUrls = new LinkedHashMap<>();
      for (ResolvedSource source : linked) {
         String sourceName = source.link().sourceName();
         baseUrls.put(sourceName, onMocks(sourceName, implementations) ? mocksOf(source)
               : implementationOf(workflowId, sourceName, implementations.baseUrls().get(sourceName)));
      }
      return baseUrls;
   }

   /** A source goes to its mocks in a run on the mocks, and in a mixed run unless it is sent to its implementation. */
   private static boolean onMocks(String sourceName, ArazzoRunImplementations implementations) {
      if (implementations == null) {
         return true;
      }
      return implementations.mixed() && !ArazzoRunImplementations.IMPLEMENTATION
            .equals(implementations.perSource().getOrDefault(sourceName, ArazzoRunImplementations.MOCK));
   }

   /**
    * A mixed run must say clearly where each source goes: only linked sources, sent to {@code mock} or
    * {@code implementation}, and no base URL for a source sent to its mocks.
    */
   private static void requireClearTargets(ArazzoDocument document, String workflowId, List<ResolvedSource> linked,
         ArazzoRunImplementations implementations) throws ArazzoRunRefusedException {
      String refusal = "Workflow " + workflowId + " cannot run: ";
      List<String> linkedNames = linked.stream().map(source -> source.link().sourceName()).toList();
      // The names first, those of perSource then those given a base URL, so that the refusal does not depend on order.
      for (String sourceName : concat(implementations.perSource().keySet(), implementations.baseUrls().keySet())) {
         if (!linkedNames.contains(sourceName)) {
            throw new ArazzoRunRefusedException(refusal + sourceName + " is not a linked source of "
                  + document.getTitle() + " " + document.getVersion());
         }
      }
      for (Map.Entry<String, String> target : implementations.perSource().entrySet()) {
         if (target.getValue() == null) {
            throw new ArazzoRunRefusedException(refusal + "source " + target.getKey() + " is sent nowhere, say "
                  + ArazzoRunImplementations.MOCK + " or " + ArazzoRunImplementations.IMPLEMENTATION);
         }
         if (!ArazzoRunImplementations.MOCK.equals(target.getValue())
               && !ArazzoRunImplementations.IMPLEMENTATION.equals(target.getValue())) {
            throw new ArazzoRunRefusedException(refusal + "source " + target.getKey() + " is sent to "
                  + target.getValue() + ", not to " + ArazzoRunImplementations.MOCK + " or "
                  + ArazzoRunImplementations.IMPLEMENTATION);
         }
      }
      for (String sourceName : linkedNames) {
         if (onMocks(sourceName, implementations) && implementations.baseUrls().containsKey(sourceName)) {
            throw new ArazzoRunRefusedException(refusal + "source " + sourceName
                  + " is sent to its mocks and given the base URL of an implementation");
         }
      }
   }

   /** The names of the first collection, then those of the second it does not hold. */
   private static List<String> concat(Collection<String> first, Collection<String> second) {
      List<String> names = new ArrayList<>(first);
      second.stream().filter(name -> !names.contains(name)).forEach(names::add);
      return names;
   }

   /** How the steps of a run reach their APIs. */
   private static WorkflowRunTarget targetOf(ArazzoRunImplementations implementations) {
      if (implementations == null) {
         return WorkflowRunTarget.MOCKS;
      }
      return implementations.mixed() ? WorkflowRunTarget.MIXED : WorkflowRunTarget.IMPLEMENTATIONS;
   }

   /** The base URL set for the implementation of a source, without its trailing slashes. */
   private static String implementationOf(String workflowId, String sourceName, String baseUrl)
         throws ArazzoRunRefusedException {
      String refusal = "Workflow " + workflowId + " cannot run against implementations: ";
      if (baseUrl == null || baseUrl.isBlank()) {
         throw new ArazzoRunRefusedException(refusal + "no base URL for source " + sourceName);
      }
      String implementation = baseUrl.strip().replaceAll("/+$", "");
      if (!ABSOLUTE_HTTP_URL.matcher(implementation).matches()) {
         throw new ArazzoRunRefusedException(refusal + "the base URL of source " + sourceName
               + " is not an absolute http or https URL: " + implementation);
      }
      return implementation;
   }

   /** The secret chosen for the calls of a run, looked up by its name as contract tests do; null for none. */
   private Secret secretOf(ArazzoRunImplementations implementations) throws ArazzoRunRefusedException {
      if (implementations == null || implementations.secretName() == null) {
         return null;
      }
      String secretName = implementations.secretName();
      List<Secret> secrets = secretRepository.findByName(secretName);
      if (secrets.isEmpty()) {
         throw new ArazzoRunRefusedException(String.format(
               "Secret '%s' not found. Please ensure the secret exists before running the workflow.", secretName));
      }
      return secrets.get(0);
   }

   /** The authorization a secret defines, as the minion applies it to every call; null for no secret. */
   private static ArazzoRunRequest.Secret authorizationOf(Secret secret) {
      return secret == null ? null
            : new ArazzoRunRequest.Secret(secret.getName(), secret.getUsername(), secret.getPassword(),
                  secret.getToken(), secret.getTokenHeader());
   }

   private Map<String, ArazzoRunRequest.Source> targets(List<ResolvedSource> linked, Map<String, String> baseUrls,
         boolean validateSchema) {
      Map<String, ArazzoRunRequest.Source> targets = new LinkedHashMap<>();
      for (ResolvedSource source : linked) {
         Map<String, ArazzoRunRequest.Operation> operations = new LinkedHashMap<>();
         for (ArazzoStepOperation operation : source.operations().values()) {
            operations.put(operation.operationId(),
                  new ArazzoRunRequest.Operation(operation.method(), operation.path()));
         }
         // The contract is sent only when the minion validates the responses against it, with the URL its relative
         // references resolve against.
         String contract = validateSchema ? openApiContractOf(source) : null;
         targets.put(source.link().sourceName(), new ArazzoRunRequest.Source(baseUrls.get(source.link().sourceName()),
               operations, contract, contract == null ? null : validationResourceUrl));
      }
      return targets;
   }

   /**
    * The main OpenAPI or Swagger contract Microcks stored for the API a source is linked to; null when none. The calls
    * a session verifies are checked against it too.
    */
   String openApiContractOf(ResolvedSource source) {
      return resourceRepository.findMainByServiceId(source.serviceId()).stream().filter(
            resource -> resource.getType() == ResourceType.OPEN_API_SPEC || resource.getType() == ResourceType.SWAGGER)
            .map(Resource::getContent).findFirst().orElse(null);
   }

   /**
    * @param runId The id of a run
    * @return The run, empty if it does not exist
    */
   public Optional<WorkflowResult> getRun(String runId) {
      return workflowResultRepository.findByIdOptional(runId);
   }

   /**
    * Record a step of a run, as the minion reports it when the step ends.
    * @param runId    The id of the run
    * @param step     The step, at the place the minion gives it in the execution order
    * @param request  The request the step sent; null when it sent none
    * @param response The response the step received; null when it received none
    * @return The run, empty if it does not exist
    */
   public Optional<WorkflowResult> recordStep(String runId, WorkflowStepResult step, Request request,
         Response response) {
      return workflowResultRepository.findByIdOptional(runId).map(run -> {
         String testCaseId = request == null ? null : runId + "-" + step.index();
         if (request != null) {
            storeExchange(testCaseId, request, response);
         }
         // The index the minion gives is the execution order: a step reported again keeps its slot.
         List<WorkflowStepResult> steps = new ArrayList<>(
               run.getSteps().stream().filter(recorded -> recorded.index() != step.index()).toList());
         steps.add(new WorkflowStepResult(step.index(), step.stepId(), step.operation(), step.status(),
               step.statusCode(), step.elapsedTime(), step.criteria(), step.outputs(), testCaseId, step.message(),
               step.requestUrl(), step.parameters(), step.outputOrigins()));
         steps.sort(Comparator.comparingInt(WorkflowStepResult::index));
         run.setSteps(steps);
         arazzoMockCoherenceService.missingExample(run, step).ifPresent(run::setMissingExample);
         return workflowResultRepository.save(run);
      });
   }

   /**
    * The request and response of a step are stored as those of tests, under the run and the index of the step; so are
    * the call a session received for a step of its verification.
    */
   void storeExchange(String testCaseId, Request request, Response response) {
      request.setTestCaseId(testCaseId);
      if (response != null) {
         response.setTestCaseId(testCaseId);
         request.setResponseId(responseRepository.save(response).getId());
      }
      requestRepository.save(request);
   }

   /**
    * Record the end of a run, as the minion reports it.
    * @param runId       The id of the run
    * @param status      How the run ended
    * @param outputs     The outputs of the workflow, by name
    * @param elapsedTime How long the run took, in milliseconds
    * @param message     Why the run failed when no step tells it; null otherwise
    * @return The run, empty if it does not exist
    */
   public Optional<WorkflowResult> recordOutcome(String runId, WorkflowRunStatus status, Map<String, Object> outputs,
         long elapsedTime, String message) {
      return workflowResultRepository.findByIdOptional(runId).map(run -> {
         run.setStatus(status);
         run.setOutputs(outputs);
         run.setElapsedTime(elapsedTime);
         run.setMessage(message);
         judgeExample(run);
         return workflowResultRepository.save(run);
      });
   }

   /** Once a run ended, the example it plays is judged on it; the run keeps its own result. */
   private static void judgeExample(WorkflowResult run) {
      if (run.getExample() != null) {
         run.setExample(ArazzoExampleJudge.judge(run.getExample(), run));
      }
   }
}
