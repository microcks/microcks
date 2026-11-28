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
package io.microcks.minion.arazzo.runner;

import java.io.IOException;
import java.time.Duration;

/**
 * Sends the requests of the steps.
 * @author SebastienDegodez
 */
public interface HttpTransport {

   /**
    * @param call    The request to send
    * @param timeout How long to wait for the response
    * @return The response
    * @throws IOException if no response is received
    */
   HttpOutcome send(HttpCall call, Duration timeout) throws IOException;
}
