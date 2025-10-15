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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@AutoConfigureMockMvc
public class UploadArtifactControllerIT extends AbstractBaseIT {

   @Autowired
   public MockMvc mockMvc;


   @Test
   public void shouldNotCreateWhenUrlIsEmpty() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download").param("url", ""))
            .andExpect(MockMvcResultMatchers.status().is(HttpStatus.NO_CONTENT.value()));
   }

   @Test
   public void shouldCreateServiceFromOpenAPIImporter() throws Exception {
      String apiPastry = "https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml";
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download").param("url", apiPastry))
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(MockMvcResultMatchers.jsonPath("$.name", Matchers.is("API Pastry - 2.0:2.0.0")));
   }

   @Test
   public void shouldCreateServiceFromPostmanCorrectly() throws Exception {
      String beerCatalogAPI = "https://raw.githubusercontent.com/microcks/microcks/master/samples/BeerCatalogAPI-collection.json";
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download").param("url", beerCatalogAPI))
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(MockMvcResultMatchers.jsonPath("$.name", Matchers.is("Beer Catalog API:0.99")));
   }

   @Test
   public void shouldNotCreateServiceWhenTheUrlIsWrong() throws Exception {
      String wrongUrl = "https://raw.githubusercontent.com/microcks/microcks/master/samples/wrong-collection.json";
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download").param("url", wrongUrl))
            .andExpect(MockMvcResultMatchers.status().isInternalServerError()).andExpect(MockMvcResultMatchers.content()
                  .string(Matchers.containsString("Exception while retrieving remote item")));
   }

   @Test
   public void shouldNotCreateServiceWithoutUrl() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download"))
            .andExpect(MockMvcResultMatchers.status().isBadRequest());
   }
}
