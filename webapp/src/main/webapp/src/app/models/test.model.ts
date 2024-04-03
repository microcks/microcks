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
import { SecretRef } from './secret.model';

export enum OAuth2GrantType {
  PASSWORD,
  CLIENT_CREDENTIALS,
  REFRESH_TOKEN
}

export class OAuth2ClientContext {
  clientId: string;
  clientSecret: string;
  tokenUri: string;
  scopes: string;
  username: string;
  password: string;
  refreshToken: string;
  grantType: OAuth2GrantType;
}

export class OAuth2AuthorizedClient {
  grantType: OAuth2GrantType;
  principalName: string;
  tokenUri: string;
  scopes: string;
}

export class TestRequest {
  serviceId: string;
  testEndpoint: string;
  runnerType: TestRunnerType;
  operationsHeaders: any;
  oAuth2Context: OAuth2ClientContext;
}

export class TestResult {
  id: string;
  version: number;
  testNumber: number;
  testDate: number;
  testedEndpoint: string;
  serviceId: string;
  timeout: number;
  elapsedTime: number;
  success: boolean = false;
  inProgress: boolean = true;
  runnerType: TestRunnerType;
  operationsHeaders: any;
  testCaseResults: TestCaseResult[];
  secretRef: SecretRef;
  authorizedClient: OAuth2AuthorizedClient;
}

export class TestCaseResult {
  success: boolean = false;
  elapsedTime: number = -1;
  operationName: string;
  testStepResults: TestStepResult[];
}

export class TestStepResult {
  success: boolean = false;
  elapsedTime: number;
  requestName: string;
  eventMessageName: string;
  message: string;
}

export enum TestRunnerType {
  HTTP,
  SOAP_HTTP,
  SOAP_UI,
  POSTMAN,
  OPEN_API_SCHEMA,
  ASYNC_API_SCHEMA,
  GRPC_PROTOBUF,
  GRAPHQL_SCHEMA
}