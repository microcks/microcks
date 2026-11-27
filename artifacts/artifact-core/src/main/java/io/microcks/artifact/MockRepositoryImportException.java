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
package io.github.microcks.util;

/**
 * Typed exception for reporting problem during a mock repository import.
 * @author laurent
 */
public class MockRepositoryImportException extends Exception {

   public MockRepositoryImportException(String message) {
      super(message);
   }

   public MockRepositoryImportException(String message, Throwable cause) {
      super(message, cause);
   }
}
