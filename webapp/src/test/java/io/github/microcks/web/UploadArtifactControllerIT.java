package io.github.microcks.web;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@AutoConfigureMockMvc
class UploadArtifactControllerIT extends AbstractBaseIT {


   @Autowired
   MockMvc mockMvc;


   @Test
   @DisplayName("Should return 204 when the url is empty")
   void shouldNotCreateWhenUrlIsEmpty() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download").param("url", ""))
            .andExpect(MockMvcResultMatchers.status().is(204));
   }

   @Test
   @DisplayName("Should return 201 when the Service is created from OpenAPI")
   void shouldCreateServiceFromOpenAPIImporter() throws Exception {
      String apiPastry = "https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml";
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download").param("url", apiPastry))
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(MockMvcResultMatchers.jsonPath("$.name", Matchers.is("API Pastry - 2.0:2.0.0")));
   }

   @Test
   @DisplayName("Should return 201 when the Service is created from Postman")
   void shouldCreateServiceFromPostmanCorrectly() throws Exception {
      String beerCatalogAPI = "https://raw.githubusercontent.com/microcks/microcks/master/samples/BeerCatalogAPI-collection.json";
      mockMvc.perform(MockMvcRequestBuilders.post("/api/artifact/download").param("url", beerCatalogAPI))
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(MockMvcResultMatchers.jsonPath("$.name", Matchers.is("Beer Catalog API:0.99")));
   }
}
