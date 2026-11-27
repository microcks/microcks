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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;

/**
 * This is a Jackson serialization MixIn coming from https://github.com/TheoKanning/openai-java. This is an extract from
 * https://github.com/TheoKanning/openai-java/blob/main/service/src/main/java/com/theokanning/openai/service/ChatCompletionRequestMixIn.java
 * to avoid pulling the whole 'service' package and its Http clients libraries. Credits to
 * <a href="https://github.com/TheoKanning">Theo Kanning</a>!
 * @author laurent
 */
public abstract class ChatCompletionRequestMixIn {

   @JsonSerialize(using = ChatCompletionRequestSerializerAndDeserializer.Serializer.class)
   @JsonDeserialize(using = ChatCompletionRequestSerializerAndDeserializer.Deserializer.class)
   abstract ChatCompletionRequest.ChatCompletionRequestFunctionCall getFunctionCall();
}
