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
package io.github.microcks.web;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.ParameterConstraintUtil;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.JsonExpressionEvaluator;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.graphql.GraphQLHttpRequest;
import io.github.microcks.util.script.ScriptEngineBinder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.Argument;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.VariableReference;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.parser.Parser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A controller for mocking GraphQL responses.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/graphql")
public class GraphQLController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GraphQLController.class);

   private static final String INTROSPECTION_SELECTION = "__schema";

   private final ServiceRepository serviceRepository;
   private final ResponseRepository responseRepository;
   private final ResourceRepository resourceRepository;
   private final ApplicationContext applicationContext;

   @Value("${mocks.enable-invocation-stats}")
   private final Boolean enableInvocationStats = null;

   private final Parser requestParser = new Parser();
   private final SchemaParser schemaParser = new SchemaParser();
   private final SchemaGenerator schemaGenerator = new SchemaGenerator();
   private final ObjectMapper mapper = new ObjectMapper();


   /**
    * Build a GraphQLController with required dependencies.
    * @param serviceRepository  The repository to access services definitions
    * @param responseRepository The repository to access responses definitions
    * @param resourceRepository The repository to access resources artifacts
    * @param applicationContext The Spring application context
    */
   public GraphQLController(ServiceRepository serviceRepository, ResponseRepository responseRepository,
         ResourceRepository resourceRepository, ApplicationContext applicationContext) {
      this.serviceRepository = serviceRepository;
      this.responseRepository = responseRepository;
      this.resourceRepository = resourceRepository;
      this.applicationContext = applicationContext;
   }


   @RequestMapping(value = "/{service}/{version}/**", method = { RequestMethod.GET, RequestMethod.POST })
   public ResponseEntity<?> execute(@PathVariable("service") String serviceName,
         @PathVariable("version") String version, @RequestParam(value = "delay", required = false) Long delay,
         @RequestBody(required = false) String body, HttpServletRequest request) {

      log.info("Servicing GraphQL mock response for service [{}, {}]", serviceName, version);
      log.debug("Request body: {}", body);

      long startTime = System.currentTimeMillis();

      // If serviceName was encoded with '+' instead of '%20', remove them.
      if (serviceName.contains("+")) {
         serviceName = serviceName.replace('+', ' ');
      }

      Service service = serviceRepository.findByNameAndVersion(serviceName, version);
      if (service == null) {
         return new ResponseEntity<>(
               String.format("The service %s with version %s does not exist!", serviceName, version),
               HttpStatus.NOT_FOUND);
      }

      GraphQLHttpRequest graphqlHttpReq;
      Document graphqlRequest;
      try {
         graphqlHttpReq = GraphQLHttpRequest.from(body, request);
         graphqlRequest = requestParser.parseDocument(graphqlHttpReq.getQuery());
      } catch (Exception e) {
         log.error("Error parsing GraphQL request: {}", e.getMessage());
         return new ResponseEntity<>("Error parsing GraphQL request: " + e.getMessage(), HttpStatus.BAD_REQUEST);
      }

      Definition<?> graphqlDef = graphqlRequest.getDefinitions().get(0);
      OperationDefinition graphqlOperation = (OperationDefinition) graphqlDef;

      log.debug("Got this graphqlOperation: {}", graphqlOperation);

      // Operation type is direct but name depends on syntax (it can be a composite one). Better to use the names of selections...
      String operationType = graphqlOperation.getOperation().toString();

      // Check is it's an introspection query to handle first.
      if ("QUERY".equals(operationType) && INTROSPECTION_SELECTION
            .equals(((Field) graphqlOperation.getSelectionSet().getSelections().get(0)).getName())) {
         log.info("Handling GraphQL schema introspection query...");
         Resource graphqlSchema = resourceRepository
               .findByServiceIdAndType(service.getId(), ResourceType.GRAPHQL_SCHEMA).get(0);

         TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(graphqlSchema.getContent());
         GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry,
               RuntimeWiring.MOCKED_WIRING);

         GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();
         ExecutionResult executionResult = graphQL.execute(graphqlHttpReq.getQuery());

         String responseContent = null;
         try {
            responseContent = mapper.writeValueAsString(executionResult);
         } catch (JsonProcessingException jpe) {
            log.error("Unknown Json processing exception", jpe);
            return new ResponseEntity<>(jpe.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
         }
         return new ResponseEntity<>(responseContent, HttpStatus.OK);
      }

      // Then deal with one or many regular GraphQL selection queries.
      List<GraphQLQueryResponse> graphqlResponses = new ArrayList<>();
      final Long[] maxDelay = { (delay == null ? 0L : delay) };

      for (Selection<?> selection : graphqlOperation.getSelectionSet().getSelections()) {
         GraphQLQueryResponse graphqlResponse = null;
         try {
            graphqlResponse = processGraphQLQuery(service, operationType, (Field) selection,
                  graphqlRequest.getDefinitionsOfType(FragmentDefinition.class), body, graphqlHttpReq, request);
            graphqlResponses.add(graphqlResponse);
            if (graphqlResponse.getOperationDelay() != null && graphqlResponse.getOperationDelay() > maxDelay[0]) {
               maxDelay[0] = graphqlResponse.getOperationDelay();
            }
         } catch (GraphQLQueryProcessingException e) {
            log.error("Caught a GraphQL processing exception", e);
            return new ResponseEntity<>(e.getMessage(), e.getStatus());
         }
      }

      /*
       * Optimized parallel version but need to better handle exception. graphqlResponses =
       * graphqlOperation.getSelectionSet().getSelections().stream().parallel().map(selection -> { try {
       * GraphQLQueryResponse graphqlResponse = processGraphQLQuery(service, operationType, (Field) selection,
       * graphqlRequest.getDefinitionsOfType(FragmentDefinition.class), body, graphqlHttpReq, request); if
       * (graphqlResponse.getOperationDelay() != null && graphqlResponse.getOperationDelay() > maxDelay[0]) {
       * maxDelay[0] = graphqlResponse.getOperationDelay(); } return graphqlResponse; } catch
       * (GraphQLQueryProcessingException e) { log.error("Caught a GraphQL processing exception", e); return null; }
       * }).collect(Collectors.toList());
       */

      // Deal with response headers.
      HttpHeaders responseHeaders = new HttpHeaders();
      for (GraphQLQueryResponse response : graphqlResponses) {
         if (response.getResponse().getHeaders() != null) {
            for (Header header : response.getResponse().getHeaders()) {
               if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(header.getName())) {
                  responseHeaders.put(header.getName(), new ArrayList<>(header.getValues()));
               }
            }
         }
      }
      if (!responseHeaders.containsKey("Content-Type") && !responseHeaders.containsKey("content-type")) {
         responseHeaders.put("Content-Type", List.of("application/json"));
      }

      // Waiting for delay if any.
      MockControllerCommons.waitForDelay(startTime, maxDelay[0]);

      // Publish an invocation event before returning if enabled.
      if (enableInvocationStats) {
         MockControllerCommons.publishMockInvocation(applicationContext, this, service,
               graphqlResponses.get(0).getResponse(), startTime);
      }

      String responseContent = null;
      JsonNode responseNode = graphqlResponses.get(0).getJsonResponse();

      // If multi-queries and aliases were used, recompose an aggregated result.
      if (graphqlResponses.size() > 1) {
         ObjectNode aggregated = mapper.createObjectNode();
         ObjectNode dataNode = aggregated.putObject("data");
         for (GraphQLQueryResponse response : graphqlResponses) {
            dataNode.set(StringUtils.defaultIfBlank(response.getAlias(), response.getOperationName()),
                  response.getJsonResponse().path("data").path(response.getOperationName()).deepCopy());
         }
         responseNode = aggregated;
      }
      try {
         responseContent = mapper.writeValueAsString(responseNode);
      } catch (JsonProcessingException e) {
         log.error("Unknown Json processing exception", e);
         return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
      }
      return new ResponseEntity<>(responseContent, responseHeaders, HttpStatus.OK);
   }

   /**
    * Process a GraphQL field selection query (an Http query may contain many field selection queries).
    * @param service             The Service this query is targeting
    * @param operationType       The type of GraphQL operation (QUERY or MUTATION)
    * @param graphqlField        The Field selection we should apply
    * @param fragmentDefinitions A list of fragment field selection
    * @param body                The Http request body
    * @param graphqlHttpReq      The Http GraphQL request wrapper
    * @param request             The bare Http Servlet request
    * @return A GraphQL query response wrapper with some elements from the Microcks domain matching Response
    * @throws GraphQLQueryProcessingException if incoming field selection query cannot be processed
    */
   protected GraphQLQueryResponse processGraphQLQuery(Service service, String operationType, Field graphqlField,
         List<FragmentDefinition> fragmentDefinitions, String body, GraphQLHttpRequest graphqlHttpReq,
         HttpServletRequest request) throws GraphQLQueryProcessingException {

      GraphQLQueryResponse result = new GraphQLQueryResponse();
      String operationName = graphqlField.getName();

      result.setAlias(graphqlField.getAlias());
      result.setOperationName(operationName);

      log.debug("Processing a '{}' operation with name '{}'", operationType, operationName);

      Operation rOperation = null;
      for (Operation operation : service.getOperations()) {
         // Select operation based on type (QUERY or MUTATION)...
         if (operation.getMethod().equals(operationType)) {
            // ... then check the operation name itself.
            if (operation.getName().equals(operationName)) {
               rOperation = operation;
               break;
            }
         }
      }

      if (rOperation != null) {
         log.debug("Found a valid operation {} with rules: {}", rOperation.getName(), rOperation.getDispatcherRules());
         String violationMsg = validateParameterConstraintsIfAny(rOperation, request);
         if (violationMsg != null) {
            throw new GraphQLQueryProcessingException(violationMsg + ". Check parameter constraints.",
                  HttpStatus.BAD_REQUEST);
         }

         // We must find dispatcher and its rules. Default to operation ones but
         // if we have a Fallback this is the one who is holding the first pass rules.
         String dispatcher = rOperation.getDispatcher();
         String dispatcherRules = rOperation.getDispatcherRules();
         FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(rOperation);
         if (fallback != null) {
            dispatcher = fallback.getDispatcher();
            dispatcherRules = fallback.getDispatcherRules();
         }

         //
         DispatchContext dispatchContext = computeDispatchCriteria(dispatcher, dispatcherRules, graphqlField,
               graphqlHttpReq.getVariables(), request, body);
         log.debug("Dispatch criteria for finding response is {}", dispatchContext.dispatchCriteria());

         // First try: using computed dispatchCriteria on main dispatcher.
         Response response = null;
         List<Response> responses = responseRepository.findByOperationIdAndDispatchCriteria(
               IdBuilder.buildOperationId(service, rOperation), dispatchContext.dispatchCriteria());
         if (!responses.isEmpty()) {
            response = responses.get(0);
         }

         if (response == null) {
            // When using the SCRIPT dispatcher, return of evaluation may be the name of response.
            log.debug("No responses with dispatch criteria, trying the name...");
            responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, rOperation),
                  dispatchContext.dispatchCriteria());
            if (!responses.isEmpty()) {
               response = responses.get(0);
            }
         }

         if (response == null && fallback != null) {
            // If we've found nothing and got a fallback, that's the moment!
            log.debug("No responses till now so far, applying the fallback...");
            responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, rOperation),
                  fallback.getFallback());
            if (!responses.isEmpty()) {
               response = responses.get(0);
            }
         }

         if (response == null) {
            // In case no response found (because dispatcher is null for example), just get one for the operation.
            // This will allow also OPTIONS operations (like pre-flight requests) with no dispatch criteria to work.
            log.debug("No responses found so far, tempting with just bare operationId...");
            responses = responseRepository.findByOperationId(IdBuilder.buildOperationId(service, rOperation));
            if (!responses.isEmpty()) {
               response = responses.get(0);
            }
         }

         if (response != null) {
            result.setResponse(response);
            result.setOperationDelay(rOperation.getDefaultDelay());

            // Prepare headers for evaluation.
            Map<String, String> evaluableHeaders = new HashMap<>();
            if (response.getHeaders() != null) {
               for (Header header : response.getHeaders()) {
                  evaluableHeaders.put(header.getName(), request.getHeader(header.getName()));
               }
            }

            // Render response content before waiting and returning.
            String responseContent = MockControllerCommons.renderResponseContent(body, null, evaluableHeaders,
                  dispatchContext.requestContext(), response);

            try {
               JsonNode responseJson = mapper.readTree(responseContent);
               filterFieldSelection(graphqlField.getSelectionSet(), fragmentDefinitions,
                     responseJson.get("data").get(operationName));
               result.setJsonResponse(responseJson);
            } catch (JsonProcessingException pe) {
               log.error("JsonProcessingException while filtering response according GraphQL field selection", pe);
               throw new GraphQLQueryProcessingException("Exception while filtering response JSON",
                     HttpStatus.INTERNAL_SERVER_ERROR);
            }

            return result;
         }
         log.debug("No response found. Throwing a BAD_REQUEST exception...");
         throw new GraphQLQueryProcessingException("No matching response found", HttpStatus.BAD_REQUEST);
      }
      log.debug("No valid operation found. Throwing a NOT_FOUND exception...");
      throw new GraphQLQueryProcessingException("No '" + operationName + "' operation found", HttpStatus.NOT_FOUND);
   }

   /** Validate the parameter constraints and return a single string with violation message if any. */
   private String validateParameterConstraintsIfAny(Operation rOperation, HttpServletRequest request) {
      if (rOperation.getParameterConstraints() != null) {
         for (ParameterConstraint constraint : rOperation.getParameterConstraints()) {
            String violationMsg = ParameterConstraintUtil.validateConstraint(request, constraint);
            if (violationMsg != null) {
               return violationMsg;
            }
         }
      }
      return null;
   }

   /** Compute a dispatch context with a dispatchCriteria string from type, rules and request elements. */
   private DispatchContext computeDispatchCriteria(String dispatcher, String dispatcherRules,
         Selection<?> graphqlSelection, JsonNode requestVariables, HttpServletRequest request, String body) {

      String dispatchCriteria = null;
      Map<String, Object> requestContext = null;

      // Depending on dispatcher, evaluate request with rules.
      if (dispatcher != null) {
         switch (dispatcher) {
            case DispatchStyles.SCRIPT:
               ScriptEngineManager sem = new ScriptEngineManager();
               requestContext = new HashMap<>();
               try {
                  // Evaluating request with script coming from operation dispatcher rules.
                  ScriptEngine se = sem.getEngineByExtension("groovy");
                  ScriptEngineBinder.bindEnvironment(se, body, requestContext, request);
                  dispatchCriteria = (String) se.eval(dispatcherRules);
               } catch (Exception e) {
                  log.error("Error during Script evaluation", e);
               }
               break;
            case DispatchStyles.QUERY_ARGS:
               Field field = (Field) graphqlSelection;
               Map<String, String> queryParams = new HashMap<>();
               for (Argument arg : field.getArguments()) {
                  if (arg.getValue() instanceof StringValue stringArg) {
                     queryParams.put(arg.getName(), stringArg.getValue());
                  } else if (arg.getValue() instanceof VariableReference varRef && requestVariables != null) {
                     queryParams.put(arg.getName(), requestVariables.path(varRef.getName()).asText(""));
                  }
               }
               dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules, queryParams);
               break;
            case DispatchStyles.JSON_BODY:
               try {
                  JsonEvaluationSpecification specification = JsonEvaluationSpecification
                        .buildFromJsonString(dispatcherRules);
                  dispatchCriteria = JsonExpressionEvaluator.evaluate(mapper.writeValueAsString(requestVariables),
                        specification);
               } catch (JsonMappingException jme) {
                  log.error("Dispatching rules of operation cannot be interpreted as JsonEvaluationSpecification", jme);
               } catch (JsonProcessingException jpe) {
                  log.error("Request variables cannot be serialized as Json for evaluation", jpe);
               }
               break;
         }
      }
      return new DispatchContext(dispatchCriteria, requestContext);
   }

   /**
    * Apply a FieldSelection filter on Json node.
    * @param selectionSet        The set of selections to apply
    * @param fragmentDefinitions A list of fragment field selection
    * @param node                The Json node to apply on
    */
   protected void filterFieldSelection(SelectionSet selectionSet, List<FragmentDefinition> fragmentDefinitions,
         JsonNode node) {
      // Stop condition: no more selection to apply.
      if (selectionSet == null || selectionSet.getSelections() == null || selectionSet.getSelections().isEmpty()) {
         return;
      }
      switch (node.getNodeType()) {
         case OBJECT:
            // We must retain properties corresponding to field selection
            // and recurse on each retrained object property.
            List<String> properties = new ArrayList<>();
            for (Selection<?> selection : selectionSet.getSelections()) {
               if (selection instanceof Field fieldSelection) {
                  filterFieldSelection(fieldSelection.getSelectionSet(), fragmentDefinitions,
                        node.get(fieldSelection.getName()));
                  properties.add(fieldSelection.getName());
               } else if (selection instanceof FragmentSpread fragmentSpread) {
                  // FragmentSpread is an indirection to selection find in definitions.
                  FragmentDefinition fragmentDef = fragmentDefinitions.stream()
                        .filter(def -> def.getName().equals(fragmentSpread.getName())).findFirst().orElse(null);
                  if (fragmentDef != null) {
                     filterFieldSelection(fragmentDef.getSelectionSet(), fragmentDefinitions, node);
                  }
               }
            }
            // Only filter if properties to retain.
            if (!properties.isEmpty()) {
               ((ObjectNode) node).retain(properties);
            }
            break;
         case ARRAY:
            // We must apply selection on each element of the array.
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
               filterFieldSelection(selectionSet, fragmentDefinitions, children.next());
            }
            break;
         default:
            break;
      }
   }

   /** Simple wrapper around a GraphQL query response. */
   protected class GraphQLQueryResponse {
      String operationName;
      String alias;
      Long operationDelay;
      Response response;
      JsonNode jsonResponse;

      public String getOperationName() {
         return operationName;
      }

      public void setOperationName(String operationName) {
         this.operationName = operationName;
      }

      public String getAlias() {
         return alias;
      }

      public void setAlias(String alias) {
         this.alias = alias;
      }

      public Long getOperationDelay() {
         return operationDelay;
      }

      public void setOperationDelay(Long operationDelay) {
         this.operationDelay = operationDelay;
      }

      public JsonNode getJsonResponse() {
         return jsonResponse;
      }

      public void setJsonResponse(JsonNode jsonResponse) {
         this.jsonResponse = jsonResponse;
      }

      public Response getResponse() {
         return response;
      }

      public void setResponse(Response response) {
         this.response = response;
      }
   }

   /** Simple exception wrapping a processing error. */
   protected static class GraphQLQueryProcessingException extends Exception {

      final HttpStatus status;

      public HttpStatus getStatus() {
         return status;
      }

      public GraphQLQueryProcessingException(String message, HttpStatus status) {
         super(message);
         this.status = status;
      }
   }
}
