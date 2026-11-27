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
package io.github.microcks.minion.async;

import io.github.microcks.minion.async.producer.ProducerManager;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean responsible for Async mock messages producers scheduling.
 * @author laurent
 */
@ApplicationScoped
public class ProducerScheduler {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private final List<TriggerKey> triggerKeys = new ArrayList<>();

   final Scheduler quartz;
   final AsyncMockRepository mockRepository;

   @ConfigProperty(name = "minion.restricted-frequencies")
   Long[] restrictedFrequencies;

   /**
    * Create a new ProducerScheduler with required dependencies.
    * @param quartz         The Quartz scheduler
    * @param mockRepository The repository for mock definitions
    */
   public ProducerScheduler(Scheduler quartz, AsyncMockRepository mockRepository) {
      this.quartz = quartz;
      this.mockRepository = mockRepository;
   }


   /** Perform a dummy action. This one is actually necessary to activate the injection of Quartz scheduler. */
   @Scheduled(every = "24h")
   public void performAction() {
      logger.debug("Performing dummy action each day to allow Quartz activation");
   }


   /** Schedule all the producer jobs for configured frequencies. */
   public void scheduleAllProducerJobs() {
      for (Long frequency : restrictedFrequencies) {
         scheduleProducerForFrequency(frequency);
      }
   }

   /** Unschedule all producer jobs for configured frequencies. */
   public void unscheduleAllProducerJobs() {
      try {
         quartz.unscheduleJobs(triggerKeys);
         triggerKeys.clear();
      } catch (SchedulerException e) {
         logger.error("Failure while unscheduling all producer jobs", e);
      }
   }

   /** Inner class implementing Quartz Job and deleting job execution to ProducerManager bean. */
   @RegisterForReflection
   public static class AsyncMockProducerJob implements Job {
      public void execute(JobExecutionContext context) throws JobExecutionException {
         Long frequency = context.getJobDetail().getJobDataMap().getLong("frequency");
         Arc.container().instance(ProducerManager.class).get().produceAsyncMockMessagesAt(frequency);
      }
   }

   /** Schedule a Quartz Job and associate trigger for specified frewqency. */
   private void scheduleProducerForFrequency(Long frequency) {
      logger.info("Scheduling a new Producer Job at frequency " + frequency);

      // Create a job detail which is holding its frequency as a param.
      JobDetail jobDetail = JobBuilder.newJob(AsyncMockProducerJob.class).usingJobData("frequency", frequency).build();

      // Create a trigger for this job, executing each 'frequency' seconds.
      Trigger trigger = TriggerBuilder.newTrigger().forJob(jobDetail).withIdentity(String.valueOf(frequency)).startNow()
            .withSchedule(
                  SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds((int) (long) frequency).repeatForever())
            .build();

      try {
         quartz.scheduleJob(jobDetail, trigger);
         triggerKeys.add(trigger.getKey());
      } catch (SchedulerException e) {
         logger.error("Failure while scheduling producer job for frequency " + frequency, e);
      }
   }
}
