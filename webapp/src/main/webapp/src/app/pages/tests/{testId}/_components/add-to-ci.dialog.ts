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
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { BsModalRef } from 'ngx-bootstrap/modal';
import { Highlight } from 'ngx-highlightjs';

import {
  NotificationService,
  NotificationType
} from '../../../../components/patternfly-ng/notification';

import { TestResult } from '../../../../models/test.model';
import { Service } from '../../../../models/service.model';


@Component({
  selector: 'app-add-to-ci-dialog',
  templateUrl: './add-to-ci.dialog.html',
  styleUrls: ['./add-to-ci.dialog.css'],
  imports: [
    CommonModule,
    FormsModule,
    Highlight
  ]
})
export class AddToCIDialogComponent implements OnInit {
  
  closeBtnName!: string;
  test!: TestResult;
  service!: Service;

  ciType?: string;

  constructor(
    public bsModalRef: BsModalRef,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {}

  private getMicrocksURL(): string {
    let microcksURL = document.location.origin;
    // Manage dev mode.
    if (microcksURL.endsWith('localhost:4200')) {
      microcksURL = 'http://localhost:8080';
    }
    return microcksURL;
  }

  public getGitHubActionCode(): string {
    let yaml = '- uses: microcks/test-github-action@v1\n';
    yaml += '  with:\n';
    yaml += `    apiNameAndVersion: '${this.service.name}:${this.service.version}'\n`;
    yaml += `    testEndpoint: '${this.test.testedEndpoint}'\n`;
    yaml += `    runner: '${this.test.runnerType}'\n`;
    yaml += `    microcksUrl: ${this.getMicrocksURL()}/api\n`;
    yaml += '    keycloakClientId: ${{ secrets.MICROCKS_SERVICE_ACCOUNT }}\n';
    yaml +=
      '    keycloakClientSecret: ${{ secrets.MICROCKS_SERVICE_ACCOUNT_SECRET }}\n';

    // Adding optional items.
    if (this.test.secretRef) {
      yaml += `    secretName: '${this.test.secretRef.name}'\n`;
    }
    if (this.test.operationsHeaders) {
      yaml += `    operationsHeaders: '${JSON.stringify(
        this.test.operationsHeaders
      )}'\n`;
    }
    // Check for filtered operations.
    if (this.test.testCaseResults.length != this.service.operations.length) {
      const operationNames = this.test.testCaseResults.map(
        (testCaseResult) => testCaseResult.operationName
      );
      yaml += `    filteredOperations: '${JSON.stringify(operationNames)}'\n`;
    }

    yaml += `    waitFor: '${this.test.timeout / 1000}sec'\n`;
    return yaml;
  }

  public getGitLabCICode(): string {
    let yaml = 'test-api\n';
    yaml += '  stage: integration-test\n';
    yaml += '  image: quay.io/microcks/microcks-cli:latest\n';
    yaml += '  variables:\n';
    yaml += `    apiNameAndVersion: "${this.service.name}:${this.service.version}"\n`;
    yaml += `    testEndpoint: ${this.test.testedEndpoint}\n`;
    yaml += `    runner: ${this.test.runnerType}\n`;
    yaml += `    microcksURL: ${this.getMicrocksURL()}/api\n`;

    // Adding optional items.
    if (this.test.secretRef) {
      yaml += `    secretName: '${this.test.secretRef.name}'\n`;
    }
    if (this.test.operationsHeaders) {
      yaml += `    operationsHeaders: '${JSON.stringify(
        this.test.operationsHeaders
      )}'\n`;
    }
    // Check for filtered operations.
    if (this.test.testCaseResults.length != this.service.operations.length) {
      const operationNames = this.test.testCaseResults.map(
        (testCaseResult) => testCaseResult.operationName
      );
      yaml += `    filteredOperations: '${JSON.stringify(operationNames)}'\n`;
    }

    yaml += `    waitFor: ${this.test.timeout / 1000}sec\n`;
    yaml += '  script:\n';
    yaml += '    - >-\n';
    yaml +=
      '      microcks-cli test "$apiNameAndVersion" $testEndpoint $runner\n';
    yaml += '      --microcksURL=$microcksURL\n';
    yaml +=
      '      --keycloakClientId=$MICROCKS_CLIENT_ID --keycloakClientSecret=$MICROCKS_CLIENT_SECRET\n';

    // Adding optional items.
    if (this.test.secretRef) {
      yaml += '      --secretName=$secretName\n';
    }
    if (this.test.operationsHeaders) {
      yaml += `      --operationsHeaders='$operationsHeaders'\n`;
    }
    // Check for filtered operations.
    if (this.test.testCaseResults.length != this.service.operations.length) {
      yaml += `      --filteredOperations='$filteredOperations'\n`;
    }
    yaml += '      --waitFor=$waitFor\n';

    return yaml;
  }

  public getJenkinsGroovyCode(): string {
    let groovy = 'microcksTest(server: \'microcks-production\',\n';
    groovy += `    serviceId: '${this.service.name}:${this.service.version}',\n`;
    groovy += `    testEndpoint: '${this.test.testedEndpoint}',\n`;
    groovy += `    runner: '${this.test.runnerType}',\n`;

    // Adding optional items.
    if (this.test.secretRef) {
      groovy += `    secretName: '${this.test.secretRef.name}',\n`;
    }
    if (this.test.operationsHeaders) {
      groovy += `    operationsHeaders: '${JSON.stringify(
        this.test.operationsHeaders
      )}',\n`;
    }

    groovy += `    waitTime: '${this.test.timeout / 1000}', waitUnit: 'sec')\n`;

    return groovy;
  }

  public getTektonCode(): string {
    let yaml = '- name: test-api\n';
    yaml += '  taskRef:\n';
    yaml += '    name: microcks-test\n';
    yaml += '  params:\n';
    yaml += '    - name: apiNameAndVersion\n';
    yaml += `      value: "${this.service.name}:${this.service.version}"\n`;
    yaml += '    - name: testEndpoint\n';
    yaml += `      value: ${this.test.testedEndpoint}\n`;
    yaml += '    - name: runner\n';
    yaml += `      value: ${this.test.runnerType}\n`;
    yaml += '    - name: microcksURL\n';
    yaml += `      value: ${this.getMicrocksURL()}/api\n`;

    // Adding optional items.
    if (this.test.secretRef) {
      yaml += '    - name: secretName\n';
      yaml += `      value: ${this.test.secretRef.name}\n`;
    }
    if (this.test.operationsHeaders) {
      yaml += '    - name: operationsHeaders\n';
      yaml += `      value: '${JSON.stringify(this.test.operationsHeaders)}'\n`;
    }
    // Check for filtered operations.
    if (this.test.testCaseResults.length != this.service.operations.length) {
      const operationNames = this.test.testCaseResults.map(
        (testCaseResult) => testCaseResult.operationName
      );
      yaml += '    - name: filteredOperations\n';
      yaml += `      value: '${JSON.stringify(operationNames)}'\n`;
    }

    yaml += '    - name: waitFor\n';
    yaml += `      value: ${this.test.timeout / 1000}sec\n`;

    return yaml;
  }

  public getCLICode(): string {
    let cmd = `./microcks-cli test '${this.service.name}:${this.service.version}' ${this.test.testedEndpoint} ${this.test.runnerType} \\ \n`;
    cmd += `  --microcksURL=${this.getMicrocksURL()}/api \\ \n`;
    cmd += `  --keycloakClientId=microcks-serviceaccount \\ \n`;
    cmd += `  --keycloakClientSecret=7deb71e8-8c80-4376-95ad-00a399ee3ca1 \\ \n`;

    // Adding optional items.
    if (this.test.secretRef) {
      cmd += `  --secretName: '${this.test.secretRef.name}' \\ \n`;
    }
    if (this.test.operationsHeaders) {
      cmd += `  --operationsHeaders: '${JSON.stringify(
        this.test.operationsHeaders
      )}' \\ \n`;
    }
    // Check for filtered operations.
    if (this.test.testCaseResults.length != this.service.operations.length) {
      const operationNames = this.test.testCaseResults.map(
        (testCaseResult) => testCaseResult.operationName
      );
      cmd += `  --filteredOperations: '${JSON.stringify(
        operationNames
      )}' \\ \n`;
    }

    cmd += `  --waitFor=${this.test.timeout / 1000}sec`;
    return cmd;
  }

  public copyToClipboard(url: string): void {
    const selBox = document.createElement('textarea');
    selBox.style.position = 'fixed';
    selBox.style.left = '0';
    selBox.style.top = '0';
    selBox.style.opacity = '0';
    selBox.value = url;
    document.body.appendChild(selBox);
    selBox.focus();
    selBox.select();
    document.execCommand('copy');
    document.body.removeChild(selBox);
    this.notificationService.message(
      NotificationType.INFO,
      this.ciType!.toUpperCase(),
      'Code has been copied to clipboard',
      false
    );
  }
}
