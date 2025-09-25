import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ModalModule } from 'ngx-bootstrap/modal';
import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { AppComponent } from './app.component';
import { AuthenticationServiceProvider } from './services/auth.service.provider';
import { ConfigService } from './services/config.service';
import { VersionInfoService } from './services/versioninfo.service';
describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AppComponent,
        BrowserAnimationsModule,
        ModalModule.forRoot(),
        BsDropdownModule.forRoot()
      ],
      providers: [
        provideRouter([]), // Empty routes for testing
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthenticationServiceProvider,
        ConfigService,
        VersionInfoService
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
