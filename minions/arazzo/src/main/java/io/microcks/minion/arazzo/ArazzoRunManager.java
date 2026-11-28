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
package io.microcks.minion.arazzo;

import io.microcks.minion.arazzo.client.MicrocksAPIConnector;
import io.microcks.minion.arazzo.client.MicrocksRunReporter;
import io.microcks.minion.arazzo.runner.JdkHttpTransport;
import io.microcks.minion.arazzo.runner.RunOutcome;
import io.microcks.minion.arazzo.runner.RunSpecification;
import io.microcks.minion.arazzo.runner.RunStatus;
import io.microcks.minion.arazzo.runner.WorkflowRunner;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs workflows in the background, each one reporting to Microcks as it goes.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoRunManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
   private final WorkflowRunner runner = new WorkflowRunner(new JdkHttpTransport(), Clock.systemUTC());

   @Inject
   @RestClient
   MicrocksAPIConnector microcksAPIConnector;

   /**
    * Start a run, without waiting for it to end.
    * @param specification What to run
    */
   public void launch(RunSpecification specification) {
      executor.submit(() -> {
         MicrocksRunReporter reporter = new MicrocksRunReporter(microcksAPIConnector, specification.runId());
         try {
            runner.run(specification, reporter);
         } catch (RuntimeException re) {
            logger.errorf(re, "Run %s of workflow %s broke", specification.runId(), specification.workflowId());
            failed(reporter, specification, re);
         }
      });
   }

   /** A run that broke must not stay in progress in Microcks: it is reported as failed, if Microcks is reachable. */
   private void failed(MicrocksRunReporter reporter, RunSpecification specification, RuntimeException re) {
      try {
         reporter.runEnded(new RunOutcome(RunStatus.FAILURE, Map.of(), 0,
               "Unexpected error in the Arazzo minion: " + re.getMessage()));
      } catch (RuntimeException unreported) {
         logger.errorf(unreported, "Run %s of workflow %s could not be reported", specification.runId(),
               specification.workflowId());
      }
   }
}
