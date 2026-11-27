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
package io.github.microcks.util.ai;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;

import java.io.IOException;

/**
 * This is a Jackson serializer/deserializer config coming from https://github.com/TheoKanning/openai-java. This is an
 * extract from
 * https://github.com/TheoKanning/openai-java/blob/main/service/src/main/java/com/theokanning/openai/service/ChatCompletionRequestSerializerAndDeserializer.java
 * to avoid pulling the whole 'service' package and its Http clients libraries. Credits to
 * <a href="https://github.com/TheoKanning">Theo Kanning</a>!
 * @author laurent
 */
public class ChatCompletionRequestSerializerAndDeserializer {

   public static class Serializer extends JsonSerializer<ChatCompletionRequest.ChatCompletionRequestFunctionCall> {
      @Override
      public void serialize(ChatCompletionRequest.ChatCompletionRequestFunctionCall value, JsonGenerator gen,
            SerializerProvider serializers) throws IOException {
         if (value == null || value.getName() == null) {
            gen.writeNull();
         } else if ("none".equals(value.getName()) || "auto".equals(value.getName())) {
            gen.writeString(value.getName());
         } else {
            gen.writeStartObject();
            gen.writeFieldName("name");
            gen.writeString(value.getName());
            gen.writeEndObject();
         }
      }
   }

   public static class Deserializer extends JsonDeserializer<ChatCompletionRequest.ChatCompletionRequestFunctionCall> {
      @Override
      public ChatCompletionRequest.ChatCompletionRequestFunctionCall deserialize(JsonParser p,
            DeserializationContext ctxt) throws IOException {
         if (p.getCurrentToken().isStructStart()) {
            p.nextToken(); //key
            p.nextToken(); //value
         }
         return new ChatCompletionRequest.ChatCompletionRequestFunctionCall(p.getValueAsString());
      }
   }
}
