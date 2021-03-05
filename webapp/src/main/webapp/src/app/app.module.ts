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
import { BrowserModule } from '@angular/platform-browser';
import { NgModule, APP_INITIALIZER } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { ModalModule } from 'ngx-bootstrap/modal';
import { TabsModule } from 'ngx-bootstrap/tabs';
import { TooltipModule } from 'ngx-bootstrap/tooltip';
import { HighlightModule, HIGHLIGHT_OPTIONS } from 'ngx-highlightjs';

import { AboutModalModule } from 'patternfly-ng/modal';
import { CardModule } from 'patternfly-ng/card';
import { DonutChartModule, SparklineChartModule } from 'patternfly-ng/chart';
import { ListModule } from 'patternfly-ng/list';
import { NotificationService, ToastNotificationListModule } from 'patternfly-ng/notification';
import { PaginationModule } from 'patternfly-ng/pagination';
import { ToolbarModule } from 'patternfly-ng/toolbar';
import { WizardModule } from 'patternfly-ng/wizard';

import { FileUploadModule } from 'ng2-file-upload';
import { TimeAgoPipe } from 'time-ago-pipe';

import { AuthenticationServiceProvider } from './services/auth.service.provider';
import { AuthenticationHttpInterceptor } from './services/auth.http-interceptor';
import { ConfigService } from './services/config.service';

import { ConfirmDeleteDialogComponent } from './components/confirm-delete/confirm-delete.component';
import { EditLabelsDialogComponent } from './components/edit-labels-dialog/edit-labels-dialog.component'
import { HelpDialogComponent } from './components/help-dialog/help-dialog.component';
import { LabelListComponent } from './components/label-list/label-list.component';
import { TestBarChartComponent } from './components/test-bar-chart/test-bar-chart.component';
import { VerticalNavComponent } from './components/vertical-nav/vertical-nav.component';

import { AdminPageComponent } from './pages/admin/admin.page';
import { SecretsTabComponent } from './pages/admin/_components/secrets.tab';
import { SnapshotsTabComponent } from './pages/admin/_components/snapshots.tab';
import { UsersTabComponent } from './pages/admin/_components/users.tab';
import { DashboardPageComponent } from './pages/dashboard/dashboard.page';
import { ServicesPageComponent } from './pages/services/services.page';
import { DynamicAPIDialogComponent } from './pages/services/_components/dynamic-api.dialog';
import { OperationOverridePageComponent } from './pages/services/{serviceId}/operation/operation-override.page';
import { ServiceDetailPageComponent } from './pages/services/{serviceId}/service-detail.page';
import { GenericResourcesDialogComponent } from './pages/services/{serviceId}/_components/generic-resources.dialog';
import { TestsPageComponent } from './pages/tests/tests.page';
import { TestCreatePageComponent } from './pages/tests/create/test-create.page';
import { TestDetailPageComponent } from './pages/tests/{testId}/test-detail.page';
import { TestRunnerPageComponent } from './pages/tests/runner/test-runner.page';
import { ImportersPageComponent, ServiceRefsDialogComponent } from './pages/importers/importers.page';
import { ImporterWizardComponent } from './pages/importers/_components/importer.wizard';
import { ArtifactUploaderDialogComponent } from './pages/importers/_components/uploader.dialog';
import { HubPageComponent } from './pages/hub/hub.page';
import { HubPackagePageComponent } from './pages/hub/package/package.page';
import { HubAPIVersionPageComponent } from './pages/hub/package/apiVersion/apiVersion.page';
import { APP_BASE_HREF } from '@angular/common';


import json from 'highlight.js/lib/languages/json';
import xml from 'highlight.js/lib/languages/xml';

/**
 * Import specific languages to avoid importing everything
 * The following will lazy load highlight.js core script (~9.6KB) + the selected languages bundle (each lang. ~1kb)
 */
export function getHighlightLanguages() {
  return [
    { name: 'json', func: json },
    { name: 'xml', func: xml }
  ];
}

export function configLoader(configService: ConfigService) {
  return () => configService.loadConfiguredFeatures();
}

@NgModule({
  declarations: [
    AppComponent, TimeAgoPipe,
    ConfirmDeleteDialogComponent, HelpDialogComponent, VerticalNavComponent,
    TestBarChartComponent, LabelListComponent, EditLabelsDialogComponent,
    DashboardPageComponent, ServicesPageComponent, DynamicAPIDialogComponent,
    ServiceDetailPageComponent, OperationOverridePageComponent, GenericResourcesDialogComponent,
    TestsPageComponent, TestCreatePageComponent, TestDetailPageComponent, TestRunnerPageComponent,
    ImportersPageComponent, ServiceRefsDialogComponent, ImporterWizardComponent, ArtifactUploaderDialogComponent,
    AdminPageComponent, SecretsTabComponent, SnapshotsTabComponent, UsersTabComponent,
    HubPageComponent, HubPackagePageComponent, HubAPIVersionPageComponent
  ],
  imports: [
    BrowserModule, FormsModule, AppRoutingModule, HttpClientModule,
    BsDropdownModule.forRoot(), ModalModule.forRoot(), TabsModule.forRoot(), TooltipModule.forRoot(),
    HighlightModule, FileUploadModule,
    AboutModalModule,
    CardModule, DonutChartModule, SparklineChartModule,
    ListModule, PaginationModule, ToolbarModule,
    WizardModule, ToastNotificationListModule,
  ],
  providers: [
    ConfigService, {
      provide: APP_INITIALIZER,
      useFactory: configLoader,
      multi: true,
      deps: [ConfigService]
    },
    AuthenticationServiceProvider, BsDropdownConfig, NotificationService,
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthenticationHttpInterceptor,
      multi: true
    },
    {
      provide: HIGHLIGHT_OPTIONS,
      useValue: {
        languages: getHighlightLanguages
      }
    },
    {
      provide: APP_BASE_HREF,
      useValue: window['base-href']
    },
  ],
  entryComponents: [
    HelpDialogComponent, DynamicAPIDialogComponent,
    EditLabelsDialogComponent, GenericResourcesDialogComponent,
    ServiceRefsDialogComponent, ImporterWizardComponent, ArtifactUploaderDialogComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
