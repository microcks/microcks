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
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule, APP_INITIALIZER } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

import { BsDatepickerModule } from 'ngx-bootstrap/datepicker';
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
import { DayInvocationsBarChartComponent } from './components/day-invocations-bar-chart/day-invocations-bar-chart-component';
import { EditLabelsComponent } from './components/edit-labels/edit-labels.component'
import { EditLabelsDialogComponent } from './components/edit-labels-dialog/edit-labels-dialog.component'
import { GradeIndexComponent } from './components/grade-index/grade-index.component';
import { HelpDialogComponent } from './components/help-dialog/help-dialog.component';
import { HourInvocationsBarChartComponent } from './components/hour-invocations-bar-chart/hour-invocations-bar-chart.component';
import { LabelListComponent } from './components/label-list/label-list.component';
import { ScoreTreemapComponent } from './components/score-treemap/score-treemap.component';
import { TestBarChartComponent } from './components/test-bar-chart/test-bar-chart.component';
import { VerticalNavComponent } from './components/vertical-nav/vertical-nav.component';

import { AdminPageComponent } from './pages/admin/admin.page';
import { SecretsTabComponent } from './pages/admin/_components/secrets.tab';
import { SnapshotsTabComponent } from './pages/admin/_components/snapshots.tab';
import { UsersTabComponent } from './pages/admin/_components/users.tab';
import { GroupsManagementDialogComponent } from './pages/admin/_components/_components/groups-management.dialog';
import { DashboardPageComponent } from './pages/dashboard/dashboard.page';
import { ServicesPageComponent } from './pages/services/services.page';
import { DirectAPIWizardComponent } from './pages/services/_components/direct-api.wizard'
import { OperationOverridePageComponent } from './pages/services/{serviceId}/operation/operation-override.page';
import { ServiceDetailPageComponent } from './pages/services/{serviceId}/service-detail.page';
import { GenerateSamplesDialogComponent } from './pages/services/{serviceId}/_components/generate-samples.dialog';
import { GenericResourcesDialogComponent } from './pages/services/{serviceId}/_components/generic-resources.dialog';
import { TestsPageComponent } from './pages/tests/tests.page';
import { TestCreatePageComponent } from './pages/tests/create/test-create.page';
import { TestDetailPageComponent } from './pages/tests/{testId}/test-detail.page';
import { AddToCIDialogComponent } from './pages/tests/{testId}/_components/add-to-ci.dialog';
import { TestRunnerPageComponent } from './pages/tests/runner/test-runner.page';
import { InvocationsServicePageComponent } from './pages/metrics/invocations/{serviceId}/invocations-service.page';
import { ImportersPageComponent, ServiceRefsDialogComponent } from './pages/importers/importers.page';
import { ImporterWizardComponent } from './pages/importers/_components/importer.wizard';
import { ArtifactUploaderDialogComponent } from './pages/importers/_components/uploader.dialog';
import { HubPageComponent } from './pages/hub/hub.page';
import { HubPackagePageComponent } from './pages/hub/package/package.page';
import { HubAPIVersionPageComponent } from './pages/hub/package/apiVersion/apiVersion.page';
import { ExchangesTabsetComponent } from './pages/services/{serviceId}/_components/exchanges-tabset/exchanges-tabset.component';
import json from 'highlight.js/lib/languages/json';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';

/**
 * Import specific languages to avoid importing everything
 * The following will lazy load highlight.js core script (~9.6KB) + the selected languages bundle (each lang. ~1kb)
 */
export function getHighlightLanguages() {
  return [
    {name: 'json', func: json},
    {name: 'xml', func: xml},
    {name: 'yaml', func: yaml},
  ];
}

export function configLoader(configService: ConfigService) {
  return () => configService.loadConfiguredFeatures();
}

@NgModule({
  declarations: [
    AppComponent, TimeAgoPipe,
    ConfirmDeleteDialogComponent, HelpDialogComponent, VerticalNavComponent, DayInvocationsBarChartComponent,
    HourInvocationsBarChartComponent, TestBarChartComponent, LabelListComponent, EditLabelsComponent, EditLabelsDialogComponent,
    ScoreTreemapComponent, GradeIndexComponent, DashboardPageComponent, ServicesPageComponent, DirectAPIWizardComponent,
    ServiceDetailPageComponent, OperationOverridePageComponent, GenerateSamplesDialogComponent, GenericResourcesDialogComponent,
    TestsPageComponent, TestCreatePageComponent, TestDetailPageComponent, AddToCIDialogComponent, TestRunnerPageComponent,
    InvocationsServicePageComponent, ImportersPageComponent, ServiceRefsDialogComponent, ImporterWizardComponent,
    ArtifactUploaderDialogComponent, AdminPageComponent, SecretsTabComponent, SnapshotsTabComponent, UsersTabComponent,
    GroupsManagementDialogComponent, HubPageComponent, HubPackagePageComponent, HubAPIVersionPageComponent,
    ExchangesTabsetComponent
  ],
  imports: [
    BrowserModule, BrowserAnimationsModule, FormsModule, AppRoutingModule, HttpClientModule,
    BsDatepickerModule.forRoot(), BsDropdownModule.forRoot(), ModalModule.forRoot(), TabsModule.forRoot(), TooltipModule.forRoot(),
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
    }
  ],
  entryComponents: [
    HelpDialogComponent, DirectAPIWizardComponent, AddToCIDialogComponent,
    EditLabelsDialogComponent, GenerateSamplesDialogComponent, GenericResourcesDialogComponent,
    ServiceRefsDialogComponent, ImporterWizardComponent, ArtifactUploaderDialogComponent,
    GroupsManagementDialogComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
