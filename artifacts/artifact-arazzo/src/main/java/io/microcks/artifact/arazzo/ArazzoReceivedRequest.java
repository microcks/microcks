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
package io.microcks.artifact.arazzo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A request an application sent for a step, read where the parameters and the request body replacements of the step put
 * their values: a path parameter in the segment its template names, a query parameter in the query string, a header by
 * its name whatever its case, a replacement at its JSON pointer in the body.
 * @param pathTemplate The path template of the operation of the step, such as /orders/{orderId}/confirm
 * @param path         The path the request was sent to
 * @param query        The query string of the request, without its ?; null when it has none
 * @param headers      The headers of the request, by name, each with its values
 * @param body         The body of the request; null when it has none
 * @author SebastienDegodez
 */
public record ArazzoReceivedRequest(String pathTemplate, String path, String query, Map<String, List<String>> headers,
      String body) {

   private static final ObjectMapper JSON = new ObjectMapper();
   private static final Pattern TEMPLATE_PARAMETER = Pattern.compile("\\{([^}]+)}");

   /**
    * @param in   Where the step puts the parameter: path, query or header
    * @param name The name of the parameter
    * @return The value the request carries there, decoded; empty when it carries none
    */
   public Optional<Object> parameter(String in, String name) {
      return switch (in) {
         case "path" -> pathParameter(name);
         case "query" -> queryParameter(name);
         case "header" -> headers.entrySet().stream().filter(header -> header.getKey().equalsIgnoreCase(name))
               .map(header -> (Object) header.getValue().get(0)).findFirst();
         default -> Optional.empty();
      };
   }

   /**
    * @param pointer The JSON pointer of a request body replacement
    * @return The value of the body at that pointer; empty when the body is not JSON or has no value there
    */
   public Optional<Object> bodyValue(String pointer) {
      try {
         return Optional.ofNullable(ArazzoJsonPointer.get(JSON.readValue(body, Object.class), pointer, "the body"));
      } catch (IllegalArgumentException | JsonProcessingException | ArazzoExpressionException noValue) {
         return Optional.empty();
      }
   }

   /** The segment of the path in the place of the parameter in the template, when the path follows the template. */
   private Optional<Object> pathParameter(String name) {
      List<String> names = new ArrayList<>();
      StringBuilder regex = new StringBuilder();
      Matcher parameters = TEMPLATE_PARAMETER.matcher(pathTemplate);
      int literal = 0;
      while (parameters.find()) {
         regex.append(Pattern.quote(pathTemplate.substring(literal, parameters.start()))).append("([^/]+)");
         names.add(parameters.group(1));
         literal = parameters.end();
      }
      regex.append(Pattern.quote(pathTemplate.substring(literal)));
      Matcher segments = Pattern.compile(regex.toString()).matcher(path);
      int group = names.indexOf(name) + 1;
      if (!segments.matches() || group == 0) {
         return Optional.empty();
      }
      return Optional.of(decode(segments.group(group)));
   }

   private Optional<Object> queryParameter(String name) {
      if (query == null) {
         return Optional.empty();
      }
      for (String pair : query.split("&")) {
         String[] nameAndValue = pair.split("=", 2);
         if (decode(nameAndValue[0]).equals(name)) {
            return Optional.of(nameAndValue.length == 2 ? decode(nameAndValue[1]) : "");
         }
      }
      return Optional.empty();
   }

   private static String decode(String value) {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
   }
}
