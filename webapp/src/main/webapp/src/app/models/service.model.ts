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
import type { Metadata } from "./commons.model";

export type Api = {
  type: ServiceType;
  name: string;
  version: string;
  resource: string;
  referencePayload: string;
};

export type Service = {
  id: string;
  name: string;
  version: string;
  xmlNS: string;
  type: ServiceType;
  operations: Operation[];
  metadata: Metadata;
  sourceArtifact: string;
};
export enum ServiceType {
  SOAP_HTTP = "SOAP_HTTP",
  REST = "REST",
  EVENT = "EVENT",
  GRPC = "GRPC",
  GENERIC_REST = "GENERIC_REST",
  GENERIC_EVENT = "GENERIC_EVENT",
  GRAPHQL = "GRAPHQL",
}

export type Operation = {
  name: string;
  method: string;
  action: string;
  inputName: string;
  outputName: string;
  bindings: { [key: string]: Binding };
  dispatcher: string;
  dispatcherRules: string;
  defaultDelay: number;
  defaultDelayStrategy: string;
  resourcePaths: string[];
  parameterConstraints: ParameterConstraint[];
};

export type OperationMutableProperties = {
  dispatcher: string;
  dispatcherRules: string;
  defaultDelay: number;
  defaultDelayStrategy: string;
  parameterConstraints: ParameterConstraint[];
};

export type Binding = {
  type: BindingType;
  keyType: string;
  destinationType: string;
  destinationName: string;
  method: string;
  qoS: string;
  persistent: boolean;
};
export enum BindingType {
  KAFKA,
  MQTT,
  NATS,
  WS,
  AMQP,
  AMQP1,
  GOOGLEPUBSUB,
  SQS,
}

export type ParameterConstraint = {
  name: string;
  in: ParameterLocation;
  required: boolean;
  recopy: boolean;
  mustMatchRegexp: string;
};
export enum ParameterLocation {
  path,
  query,
  header,
  cookie,
  formData,
  body,
}

export type Contract = {
  id: string;
  name: string;
  content: string;
  type: ContractType;
  serviceId: string;
  sourceArtifact: string;
  mainArtifact: boolean;
};
export enum ContractType {
  WSDL = "WSDL",
  XSD = "XSD",
  JSON_SCHEMA = "JSON_SCHEMA",
  SWAGGER = "SWAGGER",
  RAML = "RAML",
  OPEN_API_SPEC = "OPEN_API_SPEC",
  OPEN_API_SCHEMA = "OPEN_API_SCHEMA",
  ASYNC_API_SPEC = "ASYNC_API_SPEC",
  ASYNC_API_SCHEMA = "ASYNC_API_SCHEMA",
  AVRO_SCHEMA = "AVRO_SCHEMA",
  PROTOBUF_SCHEMA = "PROTOBUF_SCHEMA",
  PROTOBUF_DESCRIPTOR = "PROTOBUF_DESCRIPTOR",
  GRAPHQL_SCHEMA = "GRAPHQL_SCHEMA",
  POSTMAN_COLLECTION = "POSTMAN_COLLECTION",
  SOAP_UI_PROJECT = "SOAP_UI_PROJECT",
  JSON_FRAGMENT = "JSON_FRAGMENT"
}

export type Header = {
  name: string;
  values: string[];
};

export type Parameter = {
  name: string;
  value: string;
};

type Message = {
  name: string;
  content: string;
  operationId: string;
  testCaseId: string;
  sourceArtifact: string;
  headers: Header[];
};
export interface Request extends Message {
  id: string;
  responseId: string;
  queryParameters: Parameter[];
}
export interface Response extends Message {
  id: string;
  status: string;
  mediaType: string;
  dispatchCriteria: string;
  isFault: boolean;
}
export interface EventMessage extends Message {
  id: string;
  mediaType: string;
  dispatchCriteria: string;
}

export type Exchange = {
  type: string;
};
export interface UnidirectionalEvent extends Exchange {
  eventMessage: EventMessage;
}
export interface RequestResponsePair extends Exchange {
  request: Request;
  response: Response;
}

export type ServiceView = {
  service: Service;
  //messagesMap: { string: Exchange[] };
  messagesMap: Record<string, Exchange[]>;
};

export type GenericResource = {
  id: string;
  serviceId: string;
  payload: any;
};
