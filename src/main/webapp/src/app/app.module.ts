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
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { ModalModule } from 'ngx-bootstrap/modal';
import { TabsModule } from 'ngx-bootstrap/tabs';
import { TooltipModule } from 'ngx-bootstrap/tooltip';
import { HighlightModule } from 'ngx-highlightjs';

import { ListModule } from 'patternfly-ng/list';
import { NotificationService, ToastNotificationListModule } from 'patternfly-ng/notification';
import { PaginationModule } from 'patternfly-ng/pagination';
import { ToolbarModule } from 'patternfly-ng/toolbar';
import { WizardModule } from 'patternfly-ng/wizard';

import { TimeAgoPipe } from 'time-ago-pipe';

import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';

import { AuthenticationServiceProvider } from './services/auth.service.provider';
import { AuthenticationHttpInterceptor } from './services/auth.http-interceptor';
import { ConfigService } from './services/config.service';

import { ConfirmDeleteDialogComponent } from './components/confirm-delete/confirm-delete.component';
import { VerticalNavComponent } from './components/vertical-nav/vertical-nav.component';
import { TestBarChartComponent } from './components/test-bar-chart/test-bar-chart.component';

import { AdminPageComponent } from './pages/admin/admin.page';
import { DashboardPageComponent } from './pages/dashboard/dashboard.page';
import { ServicesPageComponent } from './pages/services/services.page';
import { ServiceDetailPageComponent } from './pages/services/{serviceId}/service-detail.page';
import { TestsPageComponent } from './pages/tests/tests.page';
import { TestCreatePageComponent } from './pages/tests/create/test-create.page';
import { TestDetailPageComponent } from './pages/tests/{testId}/test-detail.page';
import { TestRunnerPageComponent } from './pages/tests/runner/test-runner.page';
import { ImportersPageComponent, ServiceRefsDialogComponent } from './pages/importers/importers.page';
import { ImporterWizardComponent } from './pages/importers/_components/importer.wizard';
import { DynamicAPIDialogComponent } from './pages/services/_components/dynamic-api.dialog';
import { GenericResourcesDialogComponent } from './pages/services/{serviceId}/_components/generic-resources.dialog';


@NgModule({
  imports: [
    BrowserModule, FormsModule, BsDropdownModule.forRoot(), ModalModule.forRoot(), TabsModule.forRoot(), TooltipModule.forRoot(), 
    HighlightModule.forRoot({ theme: 'github' }), ListModule, ToastNotificationListModule, PaginationModule, ToolbarModule, WizardModule,
    AppRoutingModule, HttpClientModule
  ],
  declarations: [
    AppComponent, TimeAgoPipe, ConfirmDeleteDialogComponent, VerticalNavComponent, TestBarChartComponent, AdminPageComponent, DashboardPageComponent,
    ServicesPageComponent, ServiceDetailPageComponent, ImportersPageComponent, TestsPageComponent, TestCreatePageComponent, TestDetailPageComponent,
    TestRunnerPageComponent, ServiceRefsDialogComponent, ImporterWizardComponent, DynamicAPIDialogComponent, GenericResourcesDialogComponent
  ],
  providers: [
    ConfigService, AuthenticationServiceProvider, BsDropdownConfig, NotificationService,
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthenticationHttpInterceptor,
      multi: true
    }
  ],
  entryComponents: [
    ServiceRefsDialogComponent, ImporterWizardComponent, DynamicAPIDialogComponent, GenericResourcesDialogComponent
  ], 
  bootstrap: [AppComponent]
})
export class AppModule { }
