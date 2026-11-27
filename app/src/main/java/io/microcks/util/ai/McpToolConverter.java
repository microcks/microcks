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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.web.ResponseResult;

import org.springframework.http.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Utility base class for converting a Microcks Service and Operation into an MCP Tool.
 * @author laurent
 */
public abstract class McpToolConverter {
   protected final Service service;
   protected final Resource resource;

   public McpToolConverter(Service service, Resource resource) {
      this.service = service;
      this.resource = resource;
   }

   /**
    * Extract the name of the tool from the operation.
    * @param operation The operation to extract the name from.
    * @return The tool name
    */
   public String getToolName(Operation operation) {
      return operation.getName();
   }

   /**
    * Extract the description of the tool from the operation.
    * @param operation The operation to extract the description from.
    * @return The tool description
    */
   public abstract String getToolDescription(Operation operation);

   /**
    * Extract the input schema of the tool from the operation.
    * @param operation The operation to extract the input schema from.
    * @return The tool input schema following the 2024-11-05 MCP spec
    */
   public abstract McpSchema.JsonSchema getInputSchema(Operation operation);

   /**
    * Invoke the tool with the given request and return the response in Microcks domain object.
    * @param operation The operation to invoke the tool on.
    * @param request   The request to send to the tool.
    * @param headers   Simple representation of headers transmitted at the protocol level.
    * @return The response from the tool in Microcks domain object.
    */
   public abstract Response getCallResponse(Operation operation, McpSchema.CallToolRequest request,
         Map<String, List<String>> headers);

   /** Prepare the Http headers by sanitizing them. */
   protected Map<String, List<String>> sanitizeHttpHeaders(Map<String, List<String>> headers) {
      return headers.entrySet().stream().filter(entry -> !"content-length".equalsIgnoreCase(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
   }

   /** Depending on result encoding, extract the response content as a string. */
   protected String extractResponseContent(ResponseResult result) throws IOException {
      String responseContent = null;
      if (result.headers() != null) {
         // Response content can be compressed with gzip if we used the proxy.
         List<String> encodings = result.headers().get(HttpHeaders.CONTENT_ENCODING);
         if (encodings != null && encodings.contains("gzip")) {
            // Unzip the response content.
            try (BufferedInputStream bis = new BufferedInputStream(
                  new GZIPInputStream(new ByteArrayInputStream(result.content())))) {
               byte[] uncompressedContent = bis.readAllBytes();
               responseContent = new String(uncompressedContent, StandardCharsets.UTF_8);
            }
         }
      }
      if (responseContent == null) {
         // If no response content here, we can assume it's not compressed and can be read directly.
         responseContent = new String(result.content(), StandardCharsets.UTF_8);
      }
      return responseContent;
   }
}
