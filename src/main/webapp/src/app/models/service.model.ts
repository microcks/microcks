export class Api {
  name: string;
  version: string;
  resource: string;
}

export class Service {
  id: string;
  name: string;
  version: string;
  xmlNS: string;
  type: ServiceType;
  operations: Operation[];
}
export enum ServiceType {
  SOAP_HTTP = "SOAP_HTTP",
  REST = "REST",
  GENERIC_REST = "GENERIC_REST"
}

export class Operation {
  name: string;
  method: string;
  inputName: string;
  outputName: string;
  dispatcher: string;
  dispatcherRules: string;
  defaultDelay: number;
  resourcePaths: string[];
  parameterConstraints: ParameterConstraint[];
}

export class OperationMutableProperties {
  dispatcher: string;
  dispatcherRules: string;
  defaultDelay: number;
  parameterConstraints: ParameterConstraint[];
}
export class ParameterConstraint {
  name: string;
  in: ParameterLocation;
  required: boolean;
  recopy: boolean;
  mustMatchRegexp: string;
}
export enum ParameterLocation {
  path,
  query,
  header
}

export class Contract {
  id: string;
  name: string;
  content: string;
  type: ContractType;
  serviceId: string;
}
export enum ContractType {
  WSDL,
  XSD,
  JSON_SCHEMA,
  SWAGGER,
  RAML,
  OPEN_API_SPEC
}

export class Header {
  name: string;
  values: string[];
}

export class Parameter {
  name: string;
  value: string;
}

abstract class Message {
  name: string;
  content: string;
  operationId: string;
  testCaseId: string;
  headers: Header[];
}
export class Request extends Message {
  id: string;
  responseId: string;
  queryParameters: Parameter[];
}
export class Response extends Message {
  id: string;
  status: string;
  mediaType: string;
  dispatchCriteria: string;
  isFault: boolean = false;
}

export class RequestResponsePair {
  request: Request;
  response: Response;
}

export class ServiceView {
  service: Service;
  messagesMap: {string : RequestResponsePair[]};
}

export class GenericResource {
  id: string;
  serviceId: string;
  payload: any;
}