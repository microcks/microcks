import { Routes } from '@angular/router';

import { DashboardPageComponent } from './pages/dashboard/dashboard.page';
import { ServicesPageComponent } from './pages/services/services.page';
import { ServiceDetailPageComponent } from './pages/services/{serviceId}/service-detail.page';
import { OperationOverridePageComponent } from './pages/services/{serviceId}/operation/operation-override.page';
import { TestsPageComponent } from './pages/tests/tests.page';
import { TestCreatePageComponent } from './pages/tests/create/test-create.page';
import { TestRunnerPageComponent } from './pages/tests/runner/test-runner.page';
import { InvocationsServicePageComponent } from './pages/metrics/invocations/{serviceId}/invocations-service.page';
import { TestDetailPageComponent } from './pages/tests/{testId}/test-detail.page';
import { ImportersPageComponent } from './pages/importers/importers.page';
import { HubPageComponent } from './pages/hub/hub.page';
import { HubPackagePageComponent } from './pages/hub/package/package.page';
import { HubAPIVersionPageComponent } from './pages/hub/package/apiVersion/apiVersion.page';
import { AdminPageComponent } from './pages/admin/admin.page';
import { TracesGraphPageComponent } from './pages/traces/traces-graph.page';

export const routes: Routes = [
  {
    path: '',
    component: DashboardPageComponent
  },
  {
    path: 'services',
    component: ServicesPageComponent
  },
  {
    path: 'services/:serviceId',
    component: ServiceDetailPageComponent
  },
  {
    path: 'services/:serviceId/operation/:name',
    component: OperationOverridePageComponent
  },
  {
    path: 'tests/service/:serviceId',
    component: TestsPageComponent
  },
  {
    path: 'tests/runner/:testId',
    component: TestRunnerPageComponent
  },
  {
    path: 'tests/create',
    component: TestCreatePageComponent
  },
  {
    path: 'tests/:testId',
    component: TestDetailPageComponent
  },
  {
    path: 'metrics/invocations/:serviceName/:serviceVersion',
    component: InvocationsServicePageComponent
  },
  {
    path: 'importers',
    component: ImportersPageComponent
  },
  {
    path: 'hub',
    component: HubPageComponent
  },
  {
    path: 'hub/package/:packageId',
    component: HubPackagePageComponent
  },
  {
    path: 'hub/package/:packageId/api/:apiVersionId',
    component: HubAPIVersionPageComponent
  },
  {
    path: 'admin',
    component: AdminPageComponent
  },
  {
    path: 'traces',
    component: TracesGraphPageComponent
  }
];
