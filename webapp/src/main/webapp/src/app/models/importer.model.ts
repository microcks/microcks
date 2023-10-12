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
import { Metadata } from './commons.model';
import { SecretRef } from './secret.model';

export class ImportJob {
  id: string;
  name: string;
  repositoryUrl: string;
  mainArtifact: boolean = true;
  repositoryDisableSSLValidation: boolean = false;
  frequency: string;
  createdDate: Date;
  lastImportDate: Date;
  lastImportError: string;
  active: boolean = false;
  etag: string;

  metadata: Metadata;
  secretRef: SecretRef;
  serviceRefs: ServiceRef[];
}

export class ServiceRef {
  serviceId: string;
  name: string;
  version: string;
}