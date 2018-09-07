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

import { VerticalNavComponent } from './components/vertical-nav/vertical-nav.component';
import { TestBarChartComponent } from './components/test-bar-chart/test-bar-chart.component';

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


@NgModule({
  imports: [
    BrowserModule, FormsModule, BsDropdownModule.forRoot(), ModalModule.forRoot(), TabsModule.forRoot(), TooltipModule.forRoot(), 
    HighlightModule.forRoot({ theme: 'github' }), ListModule, ToastNotificationListModule, PaginationModule, ToolbarModule, WizardModule,
    AppRoutingModule, HttpClientModule
  ],
  declarations: [
    AppComponent, TimeAgoPipe, VerticalNavComponent, TestBarChartComponent, DashboardPageComponent, ServicesPageComponent, ServiceDetailPageComponent, 
    ImportersPageComponent, TestsPageComponent, TestCreatePageComponent, TestDetailPageComponent, TestRunnerPageComponent, ServiceRefsDialogComponent, 
    ImporterWizardComponent, DynamicAPIDialogComponent
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
    ServiceRefsDialogComponent, ImporterWizardComponent, DynamicAPIDialogComponent
  ], 
  bootstrap: [AppComponent]
})
export class AppModule { }
