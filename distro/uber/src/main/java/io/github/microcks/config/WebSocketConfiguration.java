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

import io.github.microcks.listener.WebSocketServiceChangeEventChannel;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * A configuration for enabling WebSocket and configuring CORS on our services changes notification endpoint. Only
 * activated when using the "uber" Spring profile.
 * @author laurent
 */
@Configuration
@EnableWebSocket
@Profile("uber")
public class WebSocketConfiguration implements WebSocketConfigurer {

   private final WebSocketServiceChangeEventChannel channel;

   /**
    * Build a new WebSocketConfiguration with mandatory dependencies
    * @param channel The channel to register as a handler
    */
   public WebSocketConfiguration(WebSocketServiceChangeEventChannel channel) {
      this.channel = channel;
   }

   @Override
   public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      registry.addHandler(channel, "/api/services-updates").setAllowedOrigins("*");
   }
}
