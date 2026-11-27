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

import io.github.microcks.repository.ImportJobRepository;
import io.github.microcks.security.AuthorizationChecker;
import io.github.microcks.security.UserInfo;
import io.github.microcks.service.JobService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

   private static final String UNKNOWN_JOB_ID = "unknown-job";

   @Mock
   private ImportJobRepository jobRepository;

   @Mock
   private JobService jobService;

   @Mock
   private AuthorizationChecker authorizationChecker;

   @InjectMocks
   private JobController sut;

   @Test
   void shouldReturnNotFoundWhenActivatingUnknownJob() {
      UserInfo userInfo = managerUserInfo();
      Mockito.when(jobRepository.findById(UNKNOWN_JOB_ID)).thenReturn(Optional.empty());

      ResponseEntity<?> response = sut.activateJob(UNKNOWN_JOB_ID, userInfo);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      Mockito.verifyNoInteractions(authorizationChecker);
      Mockito.verify(jobRepository, Mockito.never()).save(Mockito.any());
   }

   @Test
   void shouldReturnNotFoundWhenDeletingUnknownJob() {
      UserInfo userInfo = managerUserInfo();
      Mockito.when(jobRepository.findById(UNKNOWN_JOB_ID)).thenReturn(Optional.empty());

      ResponseEntity<?> response = sut.deleteJob(UNKNOWN_JOB_ID, userInfo);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      Mockito.verifyNoInteractions(authorizationChecker);
      Mockito.verify(jobRepository, Mockito.never()).deleteById(Mockito.anyString());
   }

   private static UserInfo managerUserInfo() {
      return new UserInfo("Test Manager", "test-manager", "Test", "Manager", "manager@example.com",
            new String[] { AuthorizationChecker.ROLE_MANAGER }, new String[0]);
   }
}
