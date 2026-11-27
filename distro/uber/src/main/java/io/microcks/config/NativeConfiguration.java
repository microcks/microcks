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
package io.microcks.config;

import io.microcks.domain.EventMessage;
import io.microcks.domain.Exchange;
import io.microcks.domain.Header;
import io.microcks.domain.Message;
import io.microcks.domain.Parameter;
import io.microcks.domain.Request;
import io.microcks.domain.RequestResponsePair;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceView;
import io.microcks.domain.UnidirectionalEvent;
import io.microcks.event.AsyncAPITriggerCommand;
import io.microcks.event.ServiceViewChangeEvent;
import io.microcks.util.ai.McpSchema;
import io.microcks.util.dispatcher.DispatchCases;
import io.microcks.util.dispatcher.FallbackSpecification;
import io.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.microcks.util.dispatcher.ProxyFallbackSpecification;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Provides the reflection hints needed when the uber distribution is compiled to a native executable. These are the
 * types that Jackson (de)serializes (WebSocket messages, MCP protocol, dispatcher specifications) and that the
 * extensions cannot discover by themselves.
 * @author laurent
 */
@RegisterForReflection(targets = { Service.class, ServiceView.class, Message.class, Header.class, Request.class,
      Response.class, Parameter.class, EventMessage.class, Exchange.class, RequestResponsePair.class,
      UnidirectionalEvent.class, ServiceViewChangeEvent.class, AsyncAPITriggerCommand.class, DispatchCases.class,
      FallbackSpecification.class, JsonEvaluationSpecification.class, ProxyFallbackSpecification.class })
public class NativeConfiguration {

   /** Registers the McpSchema nested types (requests, results, notifications...) exchanged as JSON. */
   @RegisterForReflection(targets = McpSchema.class, ignoreNested = false)
   static class McpSchemaHints {
   }

   /** Registers the ServiceLoader providers (artifact importers, test runners, gRPC transport) instantiated by name. */
   @RegisterForReflection(classNames = { "io.microcks.artifact.asyncapi.AsyncAPI3ImporterProvider",
         "io.microcks.artifact.asyncapi.AsyncAPIImporterProvider",
         "io.microcks.artifact.asyncapi.AsyncAPITestRunnerProvider",
         "io.microcks.artifact.graphql.GraphQLImporterProvider",
         "io.microcks.artifact.graphql.GraphQLTestRunnerProvider", "io.microcks.artifact.grpc.GrpcTestRunnerProvider",
         "io.microcks.artifact.grpc.ProtobufImporterProvider", "io.microcks.artifact.har.HARImporterProvider",
         "io.microcks.artifact.metadata.ExamplesExporterProvider",
         "io.microcks.artifact.metadata.ExamplesImporterProvider",
         "io.microcks.artifact.metadata.MetadataImporterProvider",
         "io.microcks.artifact.openapi.OpenAPIImporterProvider",
         "io.microcks.artifact.openapi.OpenAPIOverlayExporterProvider",
         "io.microcks.artifact.openapi.OpenAPITestRunnerProvider",
         "io.microcks.artifact.openapi.SwaggerImporterProvider",
         "io.microcks.artifact.opencollection.OpenCollectionImporterProvider",
         "io.microcks.artifact.postman.PostmanCollectionImporterProvider",
         "io.microcks.artifact.postman.PostmanTestStepsRunnerProvider",
         "io.microcks.artifact.postman.PostmanWorkspaceCollectionImporterProvider",
         "io.microcks.artifact.soapui.SoapUIAssertionsTestRunnerProvider",
         "io.microcks.artifact.soapui.SoapUIProjectImporterProvider", "io.microcks.test.HttpTestRunnerProvider",
         "io.microcks.test.SoapHttpTestRunnerProvider", "io.grpc.netty.NettyChannelProvider",
         "io.grpc.netty.UdsNettyChannelProvider", "io.grpc.netty.UdsNameResolverProvider",
         "io.grpc.internal.PickFirstLoadBalancerProvider", "io.grpc.internal.DnsNameResolverProvider" })
   static class ServiceProviderHints {
   }

   /** Registers the types that mongo-java-server persists with the Java serialization of the H2 MVStore. */
   @RegisterForReflection(serialization = true, classNames = { "de.bwaldvogel.mongo.bson.BinData",
         "de.bwaldvogel.mongo.bson.BsonJavaScript", "de.bwaldvogel.mongo.bson.BsonRegularExpression",
         "de.bwaldvogel.mongo.bson.BsonTimestamp", "de.bwaldvogel.mongo.bson.Decimal128",
         "de.bwaldvogel.mongo.bson.Document", "de.bwaldvogel.mongo.bson.Json", "de.bwaldvogel.mongo.bson.LegacyUUID",
         "de.bwaldvogel.mongo.bson.MaxKey", "de.bwaldvogel.mongo.bson.MinKey", "de.bwaldvogel.mongo.bson.ObjectId",
         "de.bwaldvogel.mongo.backend.IndexKey", "de.bwaldvogel.mongo.backend.KeyValue",
         "de.bwaldvogel.mongo.backend.Missing", "java.util.LinkedHashMap", "java.util.HashMap", "java.util.ArrayList",
         "java.util.LinkedList", "java.util.Date", "java.util.UUID", "java.time.Instant", "java.time.Ser",
         "java.math.BigDecimal", "java.math.BigInteger", "java.lang.Number", "java.lang.Long", "java.lang.Integer",
         "java.lang.Double", "java.lang.Boolean", "java.lang.String", "java.lang.Enum" })
   static class MongoSerializationHints {
   }
}
