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
package io.github.microcks.util.script;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TestFetchController {

   @GetMapping("/test-fetch")
   public ResponseEntity<String> testFetch() {
      return ResponseEntity.ok("Hello from test-fetch endpoint!");
   }

   @PostMapping("/test-fetch")
   public ResponseEntity<String> testFetchPost(@RequestBody(required = false) String body) {
      return ResponseEntity.ok("POST received with body: " + (body != null ? body : "empty"));
   }

   @PutMapping("/test-fetch")
   public ResponseEntity<String> testFetchPut(@RequestBody(required = false) String body) {
      return ResponseEntity.ok("PUT received with body: " + (body != null ? body : "empty"));
   }

   @DeleteMapping("/test-fetch")
   public ResponseEntity<String> testFetchDelete() {
      return ResponseEntity.ok("DELETE received");
   }

   @PatchMapping("/test-fetch")
   public ResponseEntity<String> testFetchPatch(@RequestBody(required = false) String body) {
      return ResponseEntity.ok("PATCH received with body: " + (body != null ? body : "empty"));
   }

   @GetMapping("/test-fetch-headers")
   public ResponseEntity<String> testFetchHeaders(HttpServletRequest request) {
      String customHeader = request.getHeader("X-Custom-Header");
      String authHeader = request.getHeader("Authorization");
      return ResponseEntity.ok("Headers received - Custom: " + customHeader + ", Auth: " + authHeader);
   }

   public static class TestFetchResponse {
      private String message;
      private int status;

      public TestFetchResponse(String message, int status) {
         this.message = message;
         this.status = status;
      }

      public String getMessage() {
         return message;
      }

      public void setMessage(String message) {
         this.message = message;
      }

      public int getStatus() {
         return status;
      }

      public void setStatus(int status) {
         this.status = status;
      }
   }

   @GetMapping("/test-fetch-json")
   public ResponseEntity<TestFetchResponse> testFetchJson() {
      TestFetchResponse response = new TestFetchResponse("Hello from test-fetch-json endpoint!", 200);
      return ResponseEntity.ok(response);
   }
}
