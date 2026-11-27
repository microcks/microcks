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
package io.microcks.web.grpc.vertx;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs tasks one at a time and in submission order on top of a shared executor. Used to keep the callbacks of a single
 * gRPC call ordered while moving them off the Vert.x event loop.
 * @author laurent
 */
class SerializingExecutor implements Executor {

   private final Executor delegate;
   private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
   private final AtomicBoolean running = new AtomicBoolean(false);

   SerializingExecutor(Executor delegate) {
      this.delegate = delegate;
   }

   @Override
   public void execute(Runnable task) {
      queue.add(task);
      schedule();
   }

   private void schedule() {
      if (running.compareAndSet(false, true)) {
         delegate.execute(this::drain);
      }
   }

   private void drain() {
      try {
         Runnable task;
         while ((task = queue.poll()) != null) {
            try {
               task.run();
            } catch (RuntimeException e) {
               Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
         }
      } finally {
         running.set(false);
         if (!queue.isEmpty()) {
            schedule();
         }
      }
   }
}
