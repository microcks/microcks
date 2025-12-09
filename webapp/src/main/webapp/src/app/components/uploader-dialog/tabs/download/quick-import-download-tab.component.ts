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
import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { NotificationService, NotificationType } from '../../../patternfly-ng/notification';
import { SecretsService } from '../../../../services/secrets.service';
import { Secret } from '../../../../models/secret.model';

@Component({
  selector: 'app-quick-import-download-tab',
  standalone: true,
  templateUrl: './quick-import-download-tab.component.html',
  styleUrls: ['./quick-import-download-tab.component.css'],
  imports: [CommonModule, ReactiveFormsModule]
})
/**
 * Download tab for the Quick Import dialog.
 *
 * Responsibilities:
 * - Let user enter a URL to a remote artifact and optional secret name
 * - Toggle whether this is the main or a secondary artifact
 * - POST to /api/artifact/download with url, mainArtifact, and secretName
 * - Close dialog on success and surface notifications
 */
export class QuickImportDownloadTabComponent implements OnInit {
  /** Optional pre-filled URL input */
  @Input() prefillUrl?: string;
  /** Reactive form holding url, secretName, and secondary flag */
  form: FormGroup;
  /** Loaded list of available secrets for dropdown */
  secrets: Secret[] = [];

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private notifications: NotificationService,
    private secretsSvc: SecretsService,
    private bsModalRef: BsModalRef,
  ) {
    this.form = this.fb.group({
      url: ['', [Validators.required]],
      secretId: ['none'],
      isSecondary: [false]
    });
  }

  ngOnInit(): void {
    // Load secrets immediately so dropdown is populated
    this.secretsSvc.getSecrets(1).subscribe(results => this.secrets = results);
    if (this.prefillUrl) {
      this.form.patchValue({ url: this.prefillUrl.trim() });
    }
  }

  setUrl(url: string): void {
    this.form.patchValue({ url: url.trim() });
  }

  /** Convenience getters */
  get url() { return this.form.get('url'); }
  get isSecondary() { return this.form.get('isSecondary'); }
  get secretId() { return this.form.get('secretId'); }

  /** Submit the download request to backend */
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const url = (this.url?.value as string).trim();
    // Determine secretName from selected secret
    let secretName = '';
    const id = (this.secretId?.value as string) || 'none';
    if (id && id !== 'none') {
      const found = this.secrets.find(s => s.id === id);
      secretName = found?.name?.trim() || '';
    }
    const mainArtifact = !(this.isSecondary?.value as boolean);

    let body = new HttpParams()
      .set('url', url)
      .set('mainArtifact', String(mainArtifact));
  if (secretName) {
      body = body.set('secretName', secretName);
    }

    const headers = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });

    this.http.post('/api/artifact/download', body.toString(), { headers, responseType: 'text' })
      .subscribe({
        next: (response: string) => {
          // parse response 
          const jsonResponse = JSON.parse(response);
          this.notifications.message(
            NotificationType.SUCCESS,
            'Artifact import',
            'Import of ' + jsonResponse.name + ' done!',
            false
          );
          this.bsModalRef.hide();
        },
        error: (err) => {
          const message = typeof err?.error === 'string' ? err.error : (err?.message || 'Unknown error');
          this.notifications.message(
            NotificationType.DANGER,
            'Artifact import failed',
            'Importation error on server side (' + message + ')',
            false
          );
        }
      });
  }
}
