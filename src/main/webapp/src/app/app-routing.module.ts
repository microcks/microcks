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
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AdminPageComponent } from './pages/admin/admin.page';
import { DashboardPageComponent } from './pages/dashboard/dashboard.page';
import { ServicesPageComponent } from './pages/services/services.page';
import { ServiceDetailPageComponent } from './pages/services/{serviceId}/service-detail.page';
import { TestsPageComponent } from './pages/tests/tests.page';
import { TestCreatePageComponent } from './pages/tests/create/test-create.page';
import { TestDetailPageComponent } from './pages/tests/{testId}/test-detail.page';
import { TestRunnerPageComponent } from './pages/tests/runner/test-runner.page';
import { ImportersPageComponent } from './pages/importers/importers.page';

const routes: Routes = [
  {
    path: '',
    component: DashboardPageComponent
  },
  {
    path: "services",
    component: ServicesPageComponent
  },
  {
    path: "services/:serviceId",
    component: ServiceDetailPageComponent
  },
  {
    path: "tests/service/:serviceId",
    component: TestsPageComponent
  },
  {
    path: "tests/runner/:testId",
    component: TestRunnerPageComponent
  },
  {
    path: "tests/create",
    component: TestCreatePageComponent
  },
  {
    path: "tests/:testId",
    component: TestDetailPageComponent
  },
  {
    path: "importers",
    component: ImportersPageComponent
  },
  {
    path: "admin",
    component: AdminPageComponent
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule],
  declarations: []
})
export class AppRoutingModule {
}