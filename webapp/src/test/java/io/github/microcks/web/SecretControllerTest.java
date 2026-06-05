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

import io.github.microcks.domain.Secret;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.security.AuthorizationChecker;
import io.github.microcks.security.UserInfo;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class SecretControllerTest {

   @Mock
   private SecretRepository secretRepository;

   @Mock
   private AuthorizationChecker authorizationChecker;

   @InjectMocks
   private SecretController sut;

   @Test
   void shouldFilterSensitiveDataWhenSearchingSecretsAsNonAdmin() {
      // arrange
      UserInfo userInfo = userInfoWithRoles(AuthorizationChecker.ROLE_USER);
      Mockito.when(secretRepository.findByNameLike("secret")).thenReturn(List.of(secret()));
      Mockito.when(authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)).thenReturn(false);

      // act
      List<Secret> results = sut.searchSecrets("secret", userInfo);

      // assert
      Secret result = results.get(0);
      SoftAssertions.assertSoftly(softly -> {
         softly.assertThat(result.getName()).isEqualTo("secret");
         softly.assertThat(result.getDescription()).isEqualTo("Secret description");
         softly.assertThat(result.getTokenHeader()).isEqualTo("Authorization");
         softly.assertThat(result.getUsername()).isNull();
         softly.assertThat(result.getPassword()).isNull();
         softly.assertThat(result.getToken()).isNull();
         softly.assertThat(result.getCaCertPem()).isNull();
      });
   }

   @Test
   void shouldKeepSensitiveDataWhenSearchingSecretsAsAdmin() {
      // arrange
      UserInfo userInfo = userInfoWithRoles(AuthorizationChecker.ROLE_ADMIN);
      Mockito.when(secretRepository.findByNameLike("secret")).thenReturn(List.of(secret()));
      Mockito.when(authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)).thenReturn(true);

      // act
      List<Secret> results = sut.searchSecrets("secret", userInfo);

      // assert
      Secret result = results.get(0);
      SoftAssertions.assertSoftly(softly -> {
         softly.assertThat(result.getUsername()).isEqualTo("user");
         softly.assertThat(result.getPassword()).isEqualTo("password");
         softly.assertThat(result.getToken()).isEqualTo("token");
         softly.assertThat(result.getCaCertPem()).isEqualTo("caCertPem");
      });
   }

   private static UserInfo userInfoWithRoles(String... roles) {
      return new UserInfo("Test User", "test-user", "Test", "User", "test@example.com", roles, new String[0]);
   }

   private static Secret secret() {
      Secret secret = new Secret();
      secret.setName("secret");
      secret.setDescription("Secret description");
      secret.setUsername("user");
      secret.setPassword("password");
      secret.setToken("token");
      secret.setTokenHeader("Authorization");
      secret.setCaCertPem("caCertPem");
      return secret;
   }
}
