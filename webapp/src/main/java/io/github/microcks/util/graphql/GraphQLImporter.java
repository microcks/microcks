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
package io.github.microcks.util.graphql;

import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeInfo;
import graphql.schema.idl.TypeUtil;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;

import graphql.language.Comment;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.parser.Parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An implementation of MockRepositoryImporter that deals with GraphQL Schema documents.
 * @author laurent
 */
public class GraphQLImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(GraphQLImporter.class);

   /** The starter marker for the comment referencing microck service and version identifiers. */
   public static final String MICROCKS_ID_STARTER = "microcksId:";

   /** The list of valid operation types. */
   public static final List<String> VALID_OPERATION_TYPES = Arrays.asList("query", "mutation");

   private final String specContent;
   private Document graphqlSchema;

   /**
    * Build a new importer.
    * @param graphqlFilePath The path to local GraphQL schema file
    * @throws IOException if project file cannot be found or read.
    */
   public GraphQLImporter(String graphqlFilePath) throws IOException {
      try {
         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(graphqlFilePath));
         specContent = new String(bytes, StandardCharsets.UTF_8);

         // Parse schema file to a dom.
         graphqlSchema = Parser.parse(specContent);
      } catch (Exception e) {
         log.error("Exception while parsing GraphQL schema file " + graphqlFilePath, e);
         throw new IOException("GraphQL schema file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> results = new ArrayList<>();

      Service service = new Service();
      service.setType(ServiceType.GRAPHQL);

      // 1st thing: look for comments to get service and version identifiers.
      for (Comment comment : graphqlSchema.getComments()) {
         String content = comment.getContent().trim();
         if (content.startsWith(MICROCKS_ID_STARTER)) {
            String identifiers = content.substring(MICROCKS_ID_STARTER.length());

            if (identifiers.indexOf(":") != -1) {
               String[] serviceAndVersion = identifiers.split(":");
               service.setName(serviceAndVersion[0].trim());
               service.setVersion(serviceAndVersion[1].trim());
               break;
            }
            log.error("microcksId comment is malformed. Expecting \'microcksId: <API_name>:<API_version>\'");
            throw new MockRepositoryImportException(
                  "microcksId comment is malformed. Expecting \'microcksId: <API_name>:<API_version>\'");
         }
      }
      if (service.getName() == null || service.getVersion() == null) {
         log.error("No microcksId: comment found into GraphQL schema to get API name and version");
         throw new MockRepositoryImportException(
               "No microcksId: comment found into GraphQL schema to get API name and version");
      }

      // We found a service, build its operations.
      service.setOperations(extractOperations());

      results.add(service);
      return results;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      List<Resource> results = new ArrayList<>();

      // Just one resource: The GraphQL schema file.
      Resource schema = new Resource();
      schema.setName(service.getName() + "-" + service.getVersion() + ".graphql");
      schema.setType(ResourceType.GRAPHQL_SCHEMA);
      schema.setContent(specContent);
      results.add(schema);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      return new ArrayList<>();
   }

   /**
    * Extract the operations from GraphQL schema document.
    */
   private List<Operation> extractOperations() {
      List<Operation> results = new ArrayList<>();

      for (Definition definition : graphqlSchema.getDefinitions()) {
         if (definition instanceof ObjectTypeDefinition typeDefinition) {

            if (VALID_OPERATION_TYPES.contains(typeDefinition.getName().toLowerCase())) {
               List<Operation> operations = extractOperations(typeDefinition);
               results.addAll(operations);
            }
         }
      }
      return results;
   }

   private List<Operation> extractOperations(ObjectTypeDefinition typeDef) {
      List<Operation> results = new ArrayList<>();

      for (FieldDefinition fieldDef : typeDef.getFieldDefinitions()) {
         Operation operation = new Operation();
         operation.setName(fieldDef.getName());
         operation.setMethod(typeDef.getName().toUpperCase());

         // Deal with input names if any.
         if (fieldDef.getInputValueDefinitions() != null && !fieldDef.getInputValueDefinitions().isEmpty()) {
            operation.setInputName(getInputNames(fieldDef.getInputValueDefinitions()));

            boolean hasOnlyPrimitiveArgs = true;
            for (InputValueDefinition inputValueDef : fieldDef.getInputValueDefinitions()) {
               Type inputValueType = inputValueDef.getType();
               if (TypeUtil.isNonNull(inputValueType)) {
                  inputValueType = TypeUtil.unwrapOne(inputValueType);
               }
               if (TypeUtil.isList(inputValueType)) {
                  hasOnlyPrimitiveArgs = false;
               }
               TypeInfo inputValueTypeInfo = TypeInfo.typeInfo(inputValueType);
               if (!ScalarInfo.isGraphqlSpecifiedScalar(inputValueTypeInfo.getName())) {
                  hasOnlyPrimitiveArgs = false;
               }
            }
            if (hasOnlyPrimitiveArgs) {
               operation.setDispatcher(DispatchStyles.QUERY_ARGS);
               operation.setDispatcherRules(extractOperationParams(fieldDef.getInputValueDefinitions()));
            }
         }
         // Deal with output names if any.
         if (fieldDef.getType() != null) {
            operation.setOutputName(getTypeName(fieldDef.getType()));
         }

         results.add(operation);
      }
      return results;
   }

   /** Build a string representing comma separated inputs (eg. 'arg1, arg2'). */
   private String getInputNames(List<InputValueDefinition> inputsDef) {
      StringBuilder builder = new StringBuilder();

      for (InputValueDefinition inputDef : inputsDef) {
         builder.append(getTypeName(inputDef.getType())).append(", ");
      }
      return builder.substring(0, builder.length() - 2);
   }

   /** Build a string representing operation parameters as used in dispatcher rules (arg1 && arg2). */
   private String extractOperationParams(List<InputValueDefinition> inputsDef) {
      StringBuilder builder = new StringBuilder();

      for (InputValueDefinition inputDef : inputsDef) {
         builder.append(inputDef.getName()).append(" && ");
      }
      return builder.substring(0, builder.length() - 4);
   }

   /** Get the short string representation of a type. eg. 'Film' or '[Films]'. */
   private String getTypeName(Type type) {
      if (type instanceof ListType listType) {
         return "[" + getTypeName(listType.getType()) + "]";
      } else if (type instanceof TypeName typeName) {
         return typeName.getName();
      }
      return type.toString();
   }
}
