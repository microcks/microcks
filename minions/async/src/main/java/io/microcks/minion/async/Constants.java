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

import java.util.Arrays;
import java.util.List;

/**
 * Some constants related to asynchronous messages used by many classes (producers, consumers, clients)
 * @author laurent
 */
public class Constants {

   /** Setup parameter telling that we have to connect to a Schema Registry for writing Avro schemas used on mocking. */
   public static final String REGISTRY_AVRO_ENCODING = "REGISTRY";

   /** Supported content-types representing Avro binary encoded content. */
   public static final List<String> AVRO_BINARY_CONTENT_TYPES = Arrays.asList("avro/binary", "application/octet-stream",
         "application/avro");

   /** Constant identifying that AMQP destination is a queue. */
   public static final String AMQP_QUEUE_TYPE = "queue";

   /** Constants identifying that AMQP destination is an exchange. */
   public static final List<String> AMQP_EXCHANGE_TYPES = Arrays.asList("direct", "topic", "fanout", "headers");
}
