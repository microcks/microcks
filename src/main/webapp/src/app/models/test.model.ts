export class TestRequest {
  serviceId: string;
  testEndpoint: string;
  runnerType: TestRunnerType;
}

export class TestResult {
  id: string;
  version: number;
  testNumber: number;
  testDate: Date;
  testedEndpoint: string;
  serviceId: string;
  elapsedTime: number;
  success: boolean = false;
  inProgress: boolean = true;
  runnerType: TestRunnerType;
  testCaseResults: TestCaseResult[];
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
  message: string;
}

export enum TestRunnerType {
  HTTP,
  SOAP_HTTP,
  SOAP_UI,
  POSTMAN,
  OPEN_API_SCHEMA
}