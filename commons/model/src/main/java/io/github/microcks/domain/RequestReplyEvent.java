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
package io.github.microcks.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a request-reply exchange with event messages. Used for asynchronous operations that follow the
 * request-reply pattern where a request event on one channel expects a correlated reply event on another channel.
 * @author adamhicks
 */
public class RequestReplyEvent extends Exchange {

   private EventMessage requestMessage;
   private EventMessage replyMessage;

   @JsonCreator
   public RequestReplyEvent(@JsonProperty("requestMessage") EventMessage requestMessage,
         @JsonProperty("replyMessage") EventMessage replyMessage) {
      this.requestMessage = requestMessage;
      this.replyMessage = replyMessage;
   }

   public EventMessage getRequestMessage() {
      return requestMessage;
   }

   public void setRequestMessage(EventMessage requestMessage) {
      this.requestMessage = requestMessage;
   }

   public EventMessage getReplyMessage() {
      return replyMessage;
   }

   public void setReplyMessage(EventMessage replyMessage) {
      this.replyMessage = replyMessage;
   }

   @Override
   public String getName() {
      return requestMessage.getName();
   }
}
