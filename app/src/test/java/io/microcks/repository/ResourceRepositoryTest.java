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
package io.github.microcks.repository;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test case for ResourceRepository implementation.
 * @author laurent
 */
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class ResourceRepositoryTest {

   @Autowired
   ResourceRepository repository;


   @BeforeEach
   public void setUp() {
      Resource resource = new Resource();
      resource.setName("GitHub OIDC-1.1.4.yaml");
      resource.setType(ResourceType.OPEN_API_SPEC);
      resource.setServiceId("6626365f24874269b65c69db");
      resource.setPath("github-oidc-1.1.4-openapi.yaml");
      resource.setMainArtifact(true);
      repository.save(resource);

      resource = new Resource();
      resource.setName("GitHub OIDC-1.1.4.yaml");
      resource.setType(ResourceType.OPEN_API_SPEC);
      resource.setServiceId("6626365f24874269b65c69db");
      resource.setPath("github-oidc-1.1.4-openapi-metadata.yaml");
      resource.setMainArtifact(false);
      repository.save(resource);
   }

   @Test
   void testFindMainByServiceIdAndType() {
      List<Resource> resources = repository.findMainByServiceId("6626365f24874269b65c69db");
      assertEquals(1, resources.size());

      Resource resource = resources.get(0);
      assertEquals("6626365f24874269b65c69db", resource.getServiceId());
      assertEquals(ResourceType.OPEN_API_SPEC, resource.getType());
      assertEquals("GitHub OIDC-1.1.4.yaml", resource.getName());
      assertEquals("github-oidc-1.1.4-openapi.yaml", resource.getPath());
   }
}
