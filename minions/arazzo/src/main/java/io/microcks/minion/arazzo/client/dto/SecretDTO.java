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
package io.microcks.minion.arazzo.client.dto;

/**
 * The secret whose authorization every call of a run carries, as Microcks sends it in a run request.
 * @param name        The name of the secret in Microcks
 * @param username    The username of Basic authentication; null for none
 * @param password    The password of Basic authentication; null for none
 * @param token       The token; null for none
 * @param tokenHeader The header carrying the token; null for Authorization: Bearer
 * @author SebastienDegodez
 */
public record SecretDTO(String name, String username, String password, String token, String tokenHeader) {
}
