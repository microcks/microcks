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
export type Secret = {
  id: string;
  name: string;
  description: string;
  username?: string;
  password?: string;
  token?: string;
  tokenHeader?: string;
  caCertPem?: string;
}

export class SecretRef {
  secretId: string;
  name: string;

  constructor(secretId: string, name: string) {
    this.secretId = secretId;
    this.name = name;
  }
}
