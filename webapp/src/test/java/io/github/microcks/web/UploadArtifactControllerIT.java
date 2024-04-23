package io.github.microcks.web;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

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
}
