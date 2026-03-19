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
package io.github.microcks.event;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A very simple Http request snapshot.
 * @param path            The path of the request.
 * @param headers         The headers of the request.
 * @param queryParameters The query parameters of the request.
 * @param body            The body of the request if any.
 * @author laurent
 */
public record HttpServletRequestSnapshot(String path, Map<String, List<String>> headers,
      Map<String, String[]> queryParameters, String body) implements Serializable {
}
