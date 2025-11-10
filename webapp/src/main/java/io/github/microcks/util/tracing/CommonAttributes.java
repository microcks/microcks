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
package io.github.microcks.util.tracing;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Common attribute keys used across tracing spans - use this one to avoid recreating AttributeKey on each event
 * message.
 * @author laurent
 */
public class CommonAttributes {

   private CommonAttributes() {
      // Private constructor to hide the implicit public one.
   }

   /** A 'service.name' span attribute. */
   public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
   /** A 'service.version' span attribute. */
   public static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");

   /** A 'operation.name' span attribute. */
   public static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("operation.name");
   /** A 'operation.action' span attribute. */
   public static final AttributeKey<String> OPERATION_ACTION = AttributeKey.stringKey("operation.action");
   /** A 'operation.method' span attribute. */
   public static final AttributeKey<String> OPERATION_METHOD = AttributeKey.stringKey("operation.method");

   /** A 'message' span attribute. */
   public static final AttributeKey<String> MESSAGE = AttributeKey.stringKey("message");

   /** A 'body.content' span attribute. */
   public static final AttributeKey<String> BODY_CONTENT = AttributeKey.stringKey("body.content");
   /** A 'body.size' span attribute. */
   public static final AttributeKey<Long> BODY_SIZE = AttributeKey.longKey("body.size");

   /** A 'http.method' span attribute. */
   public static final AttributeKey<String> HTTP_METHOD = AttributeKey.stringKey("http.method");
   /** A 'query.string' span attribute. */
   public static final AttributeKey<String> QUERY_STRING = AttributeKey.stringKey("query.string");
   /** A 'uri.full' span attribute. */
   public static final AttributeKey<String> URI_FULL = AttributeKey.stringKey("uri.full");
   /** A 'client.address' span attribute. */
   public static final AttributeKey<String> CLIENT_ADDRESS = AttributeKey.stringKey("client.address");

   /** A 'dispatcher' span attribute. */
   public static final AttributeKey<String> DISPATCHER = AttributeKey.stringKey("dispatcher");
   /** A 'dispatcher.rule' span attribute. */
   public static final AttributeKey<String> DISPATCHER_RULES = AttributeKey.stringKey("dispatcher.rules");

   /** A 'dispatch.criteria' span attribute. */
   public static final AttributeKey<String> DISPATCH_CRITERIA = AttributeKey.stringKey("dispatch.criteria");

   /** A 'delay.value' span attribute. */
   public static final AttributeKey<Long> DELAY_VALUE = AttributeKey.longKey("delay.value");
   /** A 'delay.strategy' span attribute. */
   public static final AttributeKey<String> DELAY_STRATEGY = AttributeKey.stringKey("delay.strategy");

   /** A 'script.log' span attribute. */
   public static final AttributeKey<String> SCRIPT_LOG = AttributeKey.stringKey("script.log");
   /** A 'script.engine' span attribute. */
   public static final AttributeKey<String> SCRIPT_ENGINE = AttributeKey.stringKey("script.engine");

   /** A 'response.found' span attribute. */
   public static final AttributeKey<Boolean> RESPONSE_FOUND = AttributeKey.booleanKey("response.found");
   /** A 'response.name' span attribute. */
   public static final AttributeKey<String> RESPONSE_NAME = AttributeKey.stringKey("response.name");
   /** A 'response.status' span attribute. */
   public static final AttributeKey<Long> RESPONSE_STATUS = AttributeKey.longKey("response.status");

   /** A 'error.status' span attribute. */
   public static final AttributeKey<Long> ERROR_STATUS = AttributeKey.longKey("error.status");
}
