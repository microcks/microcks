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
import {
  Operation,
  Parameter,
  ServiceType,
  ServiceView
} from '../models/service.model';

/**
 * Format the mock URL for a given service operation.
 * @param serviceView The service view containing metadata about the service.
 * @param operation The operation for which to format the URL.
 * @param urlType The type of URL to format (raw or valid).
 * @param dispatchCriteria The dispatch criteria to use for the operation.
 * @param queryParameters The query parameters to include in the URL.
 * @returns The formatted mock URL.
 */
export function formatMockUrl(serviceView: ServiceView, operation: Operation,
      urlType: 'raw' | 'valid', 
      dispatchCriteria: string | null, queryParameters: Parameter[] | null): string {
  let result = document.location.origin;

  // Manage dev mode.
  if (result.endsWith('localhost:4200')) {
    result = 'http://localhost:8080';
  }

  if (serviceView.service.type === ServiceType.REST) {
    if (urlType === 'raw') {
      result += '/rest/';
    } else {
      result += '/rest-valid/';
    }
    result +=
      encodeUrl(serviceView.service.name) +
      '/' +
      serviceView.service.version;

    const parts: Record<string, string> = {};
    const params = {};
    let operationName = operation.name;

    if (dispatchCriteria != null) {
      let partsCriteria =
        dispatchCriteria.indexOf('?') == -1
          ? dispatchCriteria
          : dispatchCriteria.substring(0, dispatchCriteria.indexOf('?'));
      const paramsCriteria =
        dispatchCriteria.indexOf('?') == -1
          ? null
          : dispatchCriteria.substring(dispatchCriteria.indexOf('?') + 1);

      partsCriteria = encodeUrl(partsCriteria);
      partsCriteria.split('/').forEach((element) => {
        if (element) {
          parts[element.split('=')[0]] = element.split('=')[1];
        }
      });

      // operationName = operationName.replace(/{(\w+)}/g, function(match, p1, string) {
      operationName = operationName.replace(
        /{([a-zA-Z0-9-_]+)}/g,
        (_, p1) => {
          return parts[p1];
        }
      );
      // Support also Postman syntax with /:part
      operationName = operationName.replace(/:([a-zA-Z0-9-_]+)/g, (_, p1) => {
        return parts[p1];
      });
      if (paramsCriteria != null && operation.dispatcher != 'QUERY_HEADER') {
        operationName += '?' + paramsCriteria.replace(/\?/g, '&');
      }
    }

    // Remove leading VERB in Postman import case.
    operationName = removeVerbInUrl(operationName);
    result += operationName;

    // Result may still contain {} if no dispatchCriteria (because of SCRIPT)
    if (result.indexOf('{') != -1 && queryParameters != null) {
      //console.log('queryParameters: ' + queryParameters);
      queryParameters.forEach((param) => {
        result = result.replace('{' + param.name + '}', param.value);
      });
    }

  } else if (serviceView.service.type === ServiceType.GRAPHQL) {
    result += '/graphql/';
    result +=
      encodeUrl(serviceView.service.name) +
      '/' +
      serviceView.service.version;
  } else if (
    serviceView.service.type === ServiceType.SOAP_HTTP
  ) {
    result += '/soap/';
    result +=
      encodeUrl(serviceView.service.name) +
      '/' +
      serviceView.service.version;
    if (urlType === 'valid') {
      result += '?validate=true';
    }
  } else if (
    serviceView.service.type === ServiceType.GENERIC_REST
  ) {
    result += '/dynarest/';
    const resourceName = removeVerbInUrl(operation.name);
    result +=
      encodeUrl(serviceView.service.name) +
      '/' +
      serviceView.service.version +
      resourceName;
  } else if (serviceView.service.type === ServiceType.GRPC) {
    // Change port in Dev mode of add '-grpc' service name suffix.
    if (result === 'http://localhost:8080') {
      result = 'http://localhost:9090';
    } else {
      result = result.replace(/^([^.-]+)(.*)/, '$1-grpc$2');
    }
  }

  return result;
}

function removeVerbInUrl(operationName: string): string {
  if (
    operationName.startsWith('GET ') ||
    operationName.startsWith('PUT ') ||
    operationName.startsWith('POST ') ||
    operationName.startsWith('DELETE ') ||
    operationName.startsWith('OPTIONS ') ||
    operationName.startsWith('PATCH ') ||
    operationName.startsWith('HEAD ') ||
    operationName.startsWith('TRACE ') ||
    operationName.startsWith('SUBSCRIBE ') ||
    operationName.startsWith('PUBLISH ') ||
    operationName.startsWith('SEND ') ||
    operationName.startsWith('RECEIVE ')
  ) {
    operationName = operationName.slice(operationName.indexOf(' ') + 1);
  }
  return operationName;
}

function encodeUrl(url: string): string {
  return url.replace(/\s/g, '+');
}