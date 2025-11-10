import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withDebugTracing, withHashLocation } from '@angular/router';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

import { BsModalService } from 'ngx-bootstrap/modal';
import { provideHighlightOptions } from 'ngx-highlightjs';

import { NotificationService } from './components/patternfly-ng/notification';

import { routes } from './app.routes';
import { AuthenticationServiceProvider } from './services/auth.service.provider';
import { ConfigService } from './services/config.service';
import { AuthenticationHttpInterceptor } from './services/auth.http-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withHashLocation()),
    //[provideRouter(routes), withDebugTracing()],
    provideHttpClient(
      // DI-based interceptors must be explicitly enabled.
      withInterceptorsFromDi(),
    ),
    provideAnimations(),
    AuthenticationServiceProvider,
    BsModalService,
    NotificationService,
    ConfigService,
    provideAppInitializer(() => {
      const configService = inject(ConfigService);
      return configService.loadConfiguredFeatures() as Promise<unknown>;
    }),

    { provide: HTTP_INTERCEPTORS, useClass: AuthenticationHttpInterceptor, multi: true },
    
    provideHighlightOptions({
      coreLibraryLoader: () => import('highlight.js/lib/core'),
      themePath: 'assets/styles/github.min.css',
      languages: {
        json: () => import('highlight.js/lib/languages/json'),
        xml: () => import('highlight.js/lib/languages/xml'),
        yaml: () => import('highlight.js/lib/languages/yaml'),
        groovy: () => import('highlight.js/lib/languages/groovy'),
        bash: () => import('highlight.js/lib/languages/bash'),
      }
    })
  ],
};
