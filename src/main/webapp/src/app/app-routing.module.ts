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
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
  declarations: []
})
export class AppRoutingModule {
}