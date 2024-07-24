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
package io.github.microcks.listener;

import io.github.microcks.domain.InvocationLogEntry;
import io.github.microcks.event.MockInvocationEvent;
import io.github.microcks.repository.InvocationLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Log invocations to database, listens to MockInvocationEvent, properties mocks.enable-invocation-logs and
 * mocks.enable-invocation-stats need to be enabled to use this.
 */
@Component
@ConditionalOnProperty(name = "mocks.enable-invocation-logs", havingValue = "true")
public class InvocationAdvancedMetrics implements ApplicationListener<MockInvocationEvent> {

   final InvocationLogRepository repo;

   public InvocationAdvancedMetrics(InvocationLogRepository repo) {
      this.repo = repo;
   }

   @Override
   public void onApplicationEvent(@NonNull MockInvocationEvent event) {
      repo.insert(new InvocationLogEntry(event.getTimestamp(), event.getServiceName(), event.getServiceVersion(),
            event.getMockResponse(), event.getInvocationTimestamp(), event.getDuration(),
            event.getSource().getClass().getSimpleName(), event.getRequestId()));
   }

   @Override
   public boolean supportsAsyncExecution() {
      return ApplicationListener.super.supportsAsyncExecution();
   }
}
