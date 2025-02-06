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

package io.github.microcks.minions.async.config;

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Message;
import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.event.ServiceViewChangeEvent;
import io.github.microcks.minion.async.client.ServiceViewChangeEventDeserializer;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.jboss.logging.Logger;

/**
 * A feature for providing runtime reflection registration hints to Graal VM.
 * @author laurent
 */
public class RuntimeReflectionRegistrationFeature implements Feature {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Override
   public void beforeAnalysis(BeforeAnalysisAccess access) {
      registerClassForReflection(ServiceViewChangeEvent.class);
      registerClassForReflection(ServiceViewChangeEventDeserializer.class);

      registerClassForReflection(ServiceView.class);
      registerClassForReflection(Metadata.class);
      registerClassForReflection(Operation.class);
      registerClassForReflection(Binding.class);
      registerClassForReflection(ParameterConstraint.class);
      registerClassForReflection(Message.class);
      registerClassForReflection(Header.class);
      registerClassForReflection(Request.class);
      registerClassForReflection(Response.class);
      registerClassForReflection(Parameter.class);
      registerClassForReflection(EventMessage.class);
      registerClassForReflection(EventMessage.class);
      registerClassForReflection(Exchange.class);
      registerClassForReflection(RequestResponsePair.class);
      registerClassForReflection(UnidirectionalEvent.class);
   }

   /**
    * Register all class elements (constructors, methods, fields) for reflection in GraalVM.
    * @param clazz The class to register.
    */
   public void registerClassForReflection(Class clazz) {
      logger.debugf("Registering %s for reflection", clazz.getCanonicalName());
      RuntimeReflection.register(clazz);
      RuntimeReflection.register(clazz.getDeclaredConstructors());
      RuntimeReflection.register(clazz.getDeclaredMethods());
      RuntimeReflection.register(clazz.getDeclaredFields());
      RuntimeReflection.register(clazz.getConstructors());
      RuntimeReflection.register(clazz.getMethods());
      RuntimeReflection.register(clazz.getFields());
   }
}
