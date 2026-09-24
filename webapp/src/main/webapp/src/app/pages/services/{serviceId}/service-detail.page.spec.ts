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
import { ServiceType } from '../../../models/service.model';
import { ServiceDetailPageComponent } from './service-detail.page';

describe('ServiceDetailPageComponent formatCurlCmd', () => {
  function createComponentForCurl(): ServiceDetailPageComponent {
    const component = Object.create(ServiceDetailPageComponent.prototype) as ServiceDetailPageComponent;

    (component as any).resolvedServiceView = {
      service: {
        type: ServiceType.REST,
        name: 'OpenAI API Documentation',
        version: '1.0.0'
      }
    };

    (component as any).formatMockUrl = () =>
      'http://localhost:9090/rest/OpenAI+API+Documentation/1.0.0/providers/openai/deployments/gpt-4o/chat/completions?api-version=2024-06-01';

    return component;
  }

  function extractAndParseCurlBody(cmd: string): unknown {
    const match = cmd.match(/ -d '([\s\S]*)'$/);
    expect(match).withContext('curl command should contain a single-quoted -d payload').not.toBeNull();

    // The generated body escapes apostrophes using POSIX shell pattern: '\''
    const payload = match![1].replace(/'\\''/g, "'");
    return JSON.parse(payload);
  }

  it('should generate a curl body that remains valid JSON when content contains apostrophes', () => {
    const component = createComponentForCurl();

    const operation: any = { method: 'POST' };
    const exchange: any = {
      request: {
        queryParameters: null,
        headers: [{ name: 'Content-Type', values: ['application/json'] }],
        content: '{"text":"Tu es un assistant d\'extraction de donnees"}'
      },
      response: { dispatchCriteria: null }
    };

    const cmd = component.formatCurlCmd(operation, exchange);

    expect(cmd).toContain("-d '{\"text\":\"Tu es un assistant d'\\''extraction de donnees\"}'");
    expect(extractAndParseCurlBody(cmd)).toEqual({
      text: "Tu es un assistant d'extraction de donnees"
    });
  });
});
