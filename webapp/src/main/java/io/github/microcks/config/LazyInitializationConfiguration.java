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

import io.github.microcks.util.grpc.GrpcServerStarter;

import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration that complements the global {@code spring.main.lazy-initialization=true} setting. It excludes from lazy
 * initialization the beans that must be eagerly created at boot time because they produce side effects during their
 * initialization (starting the gRPC mock server, configuring static utilities or installing log appenders). Beans
 * holding {@code @Scheduled} methods are already kept eager by Spring Boot own
 * {@code ScheduledBeanLazyInitializationExcludeFilter} and {@code ApplicationListener} beans are lazily instantiated on
 * first matching event by the application event multicaster.
 * @author laurent
 */
@Configuration
public class LazyInitializationConfiguration {

   @SuppressWarnings("java:S1118") // java:S1118: Suppress "Utility classes should not have public constructors" Sonar rule because this class is a Spring configuration.
   public LazyInitializationConfiguration() {
      // Prevent instantiation.
   }

   @Bean
   static LazyInitializationExcludeFilter eagerStartupBeansExcludeFilter() {
      return LazyInitializationExcludeFilter.forBeanTypes(GrpcServerStarter.class,
            ObjectMapperFactoryConfiguration.class, GraphQLParserConfiguration.class,
            OtelLoggingConfigurationInstaller.class);
   }
}
