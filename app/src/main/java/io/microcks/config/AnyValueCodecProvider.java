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
package io.microcks.config;

import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.BsonTypeClassMap;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * Stores the fields typed {@code Object} (JSON values of Arazzo runs: inputs, outputs, criteria and parameter values) as
 * the BSON value of their runtime type, and reads them back as Java values (Document, List, String, numbers, Boolean),
 * the way Spring Data MongoDB did in 1.x. Registered by Quarkus as a {@code CodecProvider} of the application.
 * @author SebastienDegodez
 */
public class AnyValueCodecProvider implements CodecProvider {

   @Override
   @SuppressWarnings("unchecked")
   public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
      return clazz == Object.class ? (Codec<T>) new AnyValueCodec(registry) : null;
   }

   /** A codec delegating to the codec of the runtime type when writing, and of the BSON type when reading. */
   static final class AnyValueCodec implements Codec<Object> {

      private static final BsonTypeClassMap BSON_TYPES = new BsonTypeClassMap();
      private final CodecRegistry registry;

      AnyValueCodec(CodecRegistry registry) {
         this.registry = registry;
      }

      @Override
      @SuppressWarnings({ "unchecked", "rawtypes" })
      public void encode(BsonWriter writer, Object value, EncoderContext encoderContext) {
         if (value == null) {
            writer.writeNull();
            return;
         }
         Codec codec = registry.get(value.getClass());
         encoderContext.encodeWithChildContext(codec, writer, value);
      }

      @Override
      public Object decode(BsonReader reader, DecoderContext decoderContext) {
         BsonType type = reader.getCurrentBsonType();
         if (type == BsonType.NULL) {
            reader.readNull();
            return null;
         }
         return decoderContext.decodeWithChildContext(registry.get(BSON_TYPES.get(type)), reader);
      }

      @Override
      public Class<Object> getEncoderClass() {
         return Object.class;
      }
   }
}
