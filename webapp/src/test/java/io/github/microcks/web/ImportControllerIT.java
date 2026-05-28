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
package io.github.microcks.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@AutoConfigureMockMvc
public class ImportControllerIT extends AbstractBaseIT {

   @Autowired
   public MockMvc mockMvc;

   @Test
   public void shouldReturnBadRequestWhenFileIsEmpty() throws Exception {
      MockMultipartFile file = new MockMultipartFile("file", "empty.json", MediaType.APPLICATION_JSON_VALUE,
            new byte[0]);

      mockMvc.perform(MockMvcRequestBuilders.multipart("/api/import").file(file))
            .andExpect(MockMvcResultMatchers.status().isBadRequest());
   }

   @Test
   public void shouldReturnUnsupportedMediaTypeWhenContentTypeIsInvalid() throws Exception {
      MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE,
            "invalid".getBytes());

      mockMvc.perform(MockMvcRequestBuilders.multipart("/api/import").file(file))
            .andExpect(MockMvcResultMatchers.status().isUnsupportedMediaType());
   }

   @Test
   public void shouldReturnInternalServerErrorWhenImportFails() throws Exception {
      MockMultipartFile file = new MockMultipartFile("file", "broken.json", MediaType.APPLICATION_JSON_VALUE,
            "{}".getBytes());

      mockMvc.perform(MockMvcRequestBuilders.multipart("/api/import").file(file))
            .andExpect(MockMvcResultMatchers.status().isInternalServerError());
   }
}
