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
package io.github.microcks.minion.async.handler;

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.ReplyInfo;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class for request-reply handlers that provides protocol-agnostic request matching, reply rendering, and
 * reply destination resolution. Protocol-specific subclasses must implement the consumer loop and reply publishing
 * logic.
 *
 * @author rootp1
 */
public abstract class AbstractRequestReplyHandler implements RequestReplyHandler {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   protected final AsyncMockDefinition mockDefinition;
   protected final Binding binding;
   private final AtomicBoolean running = new AtomicBoolean(false);

   /**
    * Create a new abstract request-reply handler.
    *
    * @param mockDefinition The mock definition containing operation and messages
    * @param binding        The protocol binding information
    */
   protected AbstractRequestReplyHandler(AsyncMockDefinition mockDefinition, Binding binding) {
      this.mockDefinition = mockDefinition;
      this.binding = binding;
   }

   @Override
   public boolean isRunning() {
      return running.get();
   }

   @Override
   public AsyncMockDefinition getMockDefinition() {
      return mockDefinition;
   }

   /**
    * Get the binding for this handler.
    *
    * @return the Binding
    */
   public Binding getBinding() {
      return binding;
   }

   /**
    * Set the running state. Subclasses should call this in their start/stop implementations.
    *
    * @param running true if the handler is running
    */
   protected void setRunning(boolean running) {
      this.running.set(running);
   }

   /**
    * Get the logger for subclasses.
    *
    * @return the logger
    */
   protected Logger getLogger() {
      return logger;
   }

   /**
    * Find the reply message that corresponds to the incoming request. Matches requests to replies using the replyId
    * field. Request messages have their replyId set to point to their corresponding reply message's ID.
    *
    * @param evaluableRequest The incoming request with body and headers
    * @return the matching reply EventMessage, or null if no match found
    */
   protected EventMessage findReplyForRequest(EvaluableRequest evaluableRequest) {
      EventMessage matchingRequest = mockDefinition.getEventMessages().stream().filter(msg -> msg.getReplyId() != null)
            .filter(msg -> EventData.matchesExpectedMessage(msg, evaluableRequest.getBody())).findFirst().orElse(null);

      if (matchingRequest == null) {
         logger.debugf("No request message definition matches incoming request");
         return null;
      }

      String replyId = matchingRequest.getReplyId();
      return mockDefinition.getEventMessages().stream().filter(msg -> replyId.equals(msg.getId())).findFirst()
            .orElse(null);
   }

   /**
    * Render reply content with any templating. If the reply content contains template expressions (e.g.
    * {@code {{request.body...}}}), they are evaluated using the template engine with the incoming request as context.
    *
    * @param replyMessage     The reply message containing the content template
    * @param evaluableRequest The incoming request to use as template context
    * @return the rendered reply content string
    */
   protected String renderReplyContent(EventMessage replyMessage, EvaluableRequest evaluableRequest) {
      String content = replyMessage.getContent();

      if (content != null && content.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         logger.debug("Reply message contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
         engine.getContext().setVariable("request", evaluableRequest);

         try {
            content = engine.getValue(content);
         } catch (Throwable t) {
            logger.errorf(t, "Failed to evaluate template '%s'", content);
         }
      }

      return content;
   }

   /**
    * Get the reply destination name. Resolves the destination from the operation's ReplyInfo, supporting both static
    * channel addresses and dynamic runtime expressions (addressLocation).
    *
    * @param evaluableRequest The incoming request, used for resolving dynamic addressLocation expressions
    * @return the reply destination name
    * @throws IllegalStateException         if no reply information is configured
    * @throws UnsupportedOperationException if the addressLocation expression cannot be resolved
    */
   protected String getReplyDestination(EvaluableRequest evaluableRequest) {
      ReplyInfo replyInfo = mockDefinition.getOperation().getReply();
      if (replyInfo == null) {
         throw new IllegalStateException(
               "Request-reply operation must have reply information, but none found for operation: "
                     + mockDefinition.getOperation().getName());
      }

      if (replyInfo.getAddressLocation() != null) {
         return ReplyAddressResolver.resolve(replyInfo.getAddressLocation(), evaluableRequest);
      }

      if (replyInfo.getChannelAddress() != null) {
         return replyInfo.getChannelAddress();
      }

      throw new IllegalStateException("Reply information must specify either channelAddress or addressLocation");
   }
}
