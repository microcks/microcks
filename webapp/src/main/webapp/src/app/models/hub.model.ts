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
export type APIPackage = {
  name: string;
  displayName: string;
  categories: string[];
  createdAt: Date;
  updatedAt: Date;
  description: string;
  imgUrl: string;
  thumbUrl: string;
  provider: string;
  source: string;
  maturity: string;
  longDescription: string;
  apis: APISummary[];
}

export type APISummary = {
  name: string;
  currentVersion: string;
  versions: APINameVersion[];
}

export type APINameVersion = {
  name: string;
  version: string;
}

export type APIVersion = {
  id: string;
  name: string;
  displayName: string;
  version: string;
  versionForCompare: string;
  createdAt: string;
  replaces: string;
  description: string;
  imgUrl: string;
  thumbUrl: string;
  capabilityLevel: string;
  contracts: Contract[];
  links: Link[];
  maintainers: Maintainer[];
  keywords: string;
  packageName: string;
}

export type Contract = {
  type: string;
  url: string;
}

export type Link = {
  name: string;
  url: string;
}

export type Maintainer = {
  name: string;
  email: string;
}
