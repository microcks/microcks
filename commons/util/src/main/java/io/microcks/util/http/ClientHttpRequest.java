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
package io.microcks.util.http;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A client-side HTTP request that can be completed with headers and body before being executed.
 * @author laurent
 */
public interface ClientHttpRequest {

   /** @return The mutable request headers */
   HttpHeaders getHeaders();

   /** @return The stream to write the request body into */
   OutputStream getBody() throws IOException;

   /** Send the request. */
   ClientHttpResponse execute() throws IOException;
}
