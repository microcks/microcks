/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
export class APIPackage {
  name: string;
  displayName: string;
  categories: string[];
  createdAt: Date;
  updatedAt: Date;
  description: string;
  imgUrl: string;
  thumbUrl: string;
  provider: string;
  maturity: string;
  longDescription: string;
  apis: APISummary[];
}

export class APISummary {
  name: string;
  currentVersion: string;
  versions: APINameVersion[];
}

export class APINameVersion {
  name: string;
  version: string;
}

export class APIVersion {
  id: string;
  name: string;
  displayName: string;
  version: string;
  versionForCompare: string;
  createdAt: string;
  replaces: string;
  provider: string;
  description: string;
  imgUrl: string;
  thumbUrl: string;
  capabilityLevel: string;
  contract: Contract;
  links: Link[];
  maintainers: Maintainer[];
  keywords: string;
  packageName: string;
}

export class Contract {
  type: string;
  url: string;
}

export class Link {
  name: string;
  url: string;
}

export class Maintainer {
  name: string;
  email: string;
}