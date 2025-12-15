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
package io.github.microcks.config;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Message;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.event.ServiceViewChangeEvent;
import io.github.microcks.event.ServiceViewChangeEventSerializer;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.util.dispatcher.DispatchCases;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;

import groovy.transform.ASTTest;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

/**
 * A configuration for providing native compilation hints to Graal VM.
 * @author laurent
 */
@Configuration
@Profile("uber")
@ImportRuntimeHints(NativeConfiguration.NativeRuntimeHints.class)
public class NativeConfiguration {

   static class NativeRuntimeHints implements RuntimeHintsRegistrar {
      @Override
      public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
         hints.reflection().registerType(TypeReference.of(Service.class));

         hints.reflection().registerType(TypeReference.of(ServiceView.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(Message.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(Header.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(Request.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(Response.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(Parameter.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(EventMessage.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(Exchange.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(RequestResponsePair.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(UnidirectionalEvent.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

         hints.reflection().registerType(TypeReference.of(ServiceViewChangeEvent.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(ServiceViewChangeEventSerializer.class),
               MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
               MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(DispatchCases.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(FallbackSpecification.class), MemberCategory.DECLARED_FIELDS,
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(JsonEvaluationSpecification.class),
               MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
               MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(ProxyFallbackSpecification.class),
               MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
               MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

         // Register all inner classes in io.github.microcks.util.ai.McpSchema class.
         Arrays.stream(io.github.microcks.util.ai.McpSchema.class.getClasses()).forEach(clazz -> {
            hints.reflection().registerType(clazz, MemberCategory.DECLARED_FIELDS,
                  MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            // Then recurse on subclasses as well...
            Arrays.stream(clazz.getClasses()).forEach(aClazz -> {
               hints.reflection().registerType(aClazz, MemberCategory.DECLARED_FIELDS,
                     MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            });
         });

         hints.reflection().registerType(
               TypeReference.of("org.springframework.security.web.access.HandlerMappingIntrospectorRequestTransformer"),
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(
               "org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$CompositeFilterChainProxy"),
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
         hints.reflection().registerType(TypeReference.of(
               "org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$HandlerMappingIntrospectorCachFilterFactoryBean"),
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

         hints.reflection().registerType(TypeReference.of("io.grpc.netty.shaded.io.netty.util.ReferenceCountUtil"),
               MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
      }
   }
}
