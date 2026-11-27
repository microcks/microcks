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

import io.github.microcks.repository.SecretRepository;
import io.github.microcks.service.ArtifactInfo;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.ReferenceResolver;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class UploadArtifactControllerTest {

   @Mock
   private ServiceService serviceService;

   @Mock
   private SecretRepository secretRepository;

   @InjectMocks
   private UploadArtifactController sut;

   @Test
   void shouldReturnBadRequest() throws MockRepositoryImportException {
      // arrange
      String apiPastry = "https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml";

      Mockito.when(serviceService.importServiceDefinition(Mockito.any(File.class), Mockito.any(ReferenceResolver.class),
            Mockito.any(ArtifactInfo.class))).thenThrow(new MockRepositoryImportException("Intentional error"));

      // act
      ResponseEntity<String> responseEntity = sut.importArtifact(apiPastry, false, null);

      // assert
      SoftAssertions.assertSoftly(softly -> {
         softly.assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
         softly.assertThat(responseEntity.getBody()).contains("Intentional error");
      });
   }

   @Test
   @DisplayName("Should return 500 when there is an error retrieving remote item")
   void shouldReturnInternalServerError() throws MockRepositoryImportException {
      // arrange
      String wrongUrl = "https://raw.githubusercontent.com/microcks/microcks/master/samples/wrong-openapi.yaml";

      // act
      ResponseEntity<String> responseEntity = sut.importArtifact(wrongUrl, false, null);

      // assert
      SoftAssertions.assertSoftly(softly -> {
         softly.assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
         softly.assertThat(responseEntity.getBody()).contains("Exception while retrieving remote item");
      });
   }

   @Test
   void shouldReturnNoContentWhenTheServiceHasNotBeenCreated() throws MockRepositoryImportException {
      // arrange
      Mockito.when(serviceService.importServiceDefinition(Mockito.any(File.class), Mockito.any(ReferenceResolver.class),
            Mockito.any(ArtifactInfo.class))).thenReturn(Collections.emptyList());

      String wrongUrl = "https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml";

      // act
      ResponseEntity<String> responseEntity = sut.importArtifact(wrongUrl, false, null);

      // assert
      Assertions.assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
   }

   @Test
   void shouldReturnNoContentWhenTheServiceHasNotBeenCreatedNullValue() throws MockRepositoryImportException {
      // arrange
      Mockito.when(serviceService.importServiceDefinition(Mockito.any(File.class), Mockito.any(ReferenceResolver.class),
            Mockito.any(ArtifactInfo.class))).thenReturn(null);

      String wrongUrl = "https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml";

      // act
      ResponseEntity<String> responseEntity = sut.importArtifact(wrongUrl, false, null);

      // assert
      Assertions.assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
   }

}
