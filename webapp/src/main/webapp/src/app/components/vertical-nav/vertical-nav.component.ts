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
import {
  Component,
  OnInit,
  TemplateRef,
  ViewEncapsulation,
  ChangeDetectionStrategy,
  AfterViewInit,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { Router, NavigationStart, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { filter } from 'rxjs/operators';

import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { BsModalRef, BsModalService, ModalModule } from 'ngx-bootstrap/modal';
import { HighlightLoader } from 'ngx-highlightjs';

import { AboutModalConfig, AboutModalEvent, AboutModalModule } from '../patternfly-ng/modal';

import { HelpDialogComponent } from '../help-dialog/help-dialog.component';

import { IAuthenticationService } from '../../services/auth.service';
import { VersionInfoService } from '../../services/versioninfo.service';
import { User } from '../../models/user.model';
import { ConfigService } from '../../services/config.service';
import { KeycloakAuthenticationService } from '../../services/auth-keycloak.service';
import { UploaderDialogService } from '../../services/uploader-dialog.service';

// Thanks to https://github.com/onokumus/metismenu/issues/110#issuecomment-317254128
//import * as $ from 'jquery';
declare let $: any;

@Component({
  selector: 'app-vertical-nav',
  encapsulation: ViewEncapsulation.None,
  templateUrl: './vertical-nav.component.html',
  styleUrls: ['./vertical-nav.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AboutModalModule,
    BsDropdownModule,
    CommonModule,
    ModalModule,
    RouterLink
  ],
})
export class VerticalNavComponent implements OnInit, AfterViewInit {
  aboutConfig: AboutModalConfig = {};
  modalRef?: BsModalRef;

  private hljsLoader: HighlightLoader = inject(HighlightLoader);

  constructor(
    protected authService: IAuthenticationService,
    private modalService: BsModalService,
    private versionInfoSvc: VersionInfoService,
    private config: ConfigService,
    private router: Router,
    private uploaderDialogService: UploaderDialogService
  ) {}

  ngOnInit() {
    this.user().subscribe((currentUser) => {
      this.versionInfoSvc.getVersionInfo().subscribe((versionInfo) => {
        this.aboutConfig = {
          additionalInfo:
            'Microcks is Open Source mocking and testing platform for API and microservices. Visit https://microcks.io for more information.',
          copyright: 'Distributed under Apache Licence v2.0',
          logoImageAlt: 'Microcks',
          logoImageSrc: 'assets/microcks.png',
          title: 'About Microcks',
          productInfo: [
            { name: 'Version', value: versionInfo.versionId },
            { name: 'Build timestamp', value: versionInfo.buildTimestamp },
            { name: 'User Login', value: currentUser.login },
            { name: 'User Name', value: currentUser.name },
          ],
        } as AboutModalConfig;
      });
    });

    this.router.events
      .pipe(filter((event) => event instanceof NavigationStart))
      .subscribe((event: NavigationStart) => {
        // Do something with the NavigationStart event object.
        //console.log('Navigation start event: ' + JSON.stringify(event));
        const navigationEvent = { type: 'navigationEvent', url: event.url };
        try {
          window.parent.postMessage(JSON.stringify(navigationEvent), '*');
        } catch (error) {
          console.warn('Error while posting navigationEvent to parent', error);
        }

        // Navigation between sections may happen outside vertical navigation (ex: when we move from Hub to
        // freshly installed API or Service). We then have to refresh the active class on the correct section.
        let section = '/';
        if (event.url != '/') {
          if (event.url.substring(1).indexOf('/') > 0) {
            section += event.url.substring(
              1,
              event.url.substring(1).indexOf('/') + 1
            );
          } else {
            // Default to short section name.
            section = event.url;
          }
        }
        $(
          'div.nav-pf-vertical-with-sub-menus li.list-group-item a[routerLink="' +
            section +
            '"]'
        )
          .parent()
          .addClass('active');
        $(
          'div.nav-pf-vertical-with-sub-menus li.list-group-item a[routerLink!="' +
            section +
            '"]'
        )
          .parent()
          .removeClass('active');
      });

    const theme = localStorage.getItem('microcks-theme');
    if (theme && theme === 'dark') {
      document.body.classList.remove('light');
      document.body.classList.add('dark');
      this.hljsLoader.setTheme('assets/styles/github-dark.min.css');
    } else {
      document.body.classList.remove('dark');
      document.body.classList.add('light');
      this.hljsLoader.setTheme('assets/styles/github.min.css');
    }
  }

  ngAfterViewInit() {
    $().setupVerticalNavigation(true);
  }

  public openHelpDialog() {
    const initialState = {};
    this.modalRef = this.modalService.show(HelpDialogComponent, {
      initialState,
    });
  }

  public openAboutModal(template: TemplateRef<any>): void {
    this.modalRef = this.modalService.show(template);
  }
  public closeAboutModal($event: AboutModalEvent): void {
    if (this.modalRef) {
      this.modalRef.hide();
    }
  }

  public user(): Observable<User> {
    return this.authService.getAuthenticatedUser();
  }

  /**
   * Checks if authentication is enabled using the configurationService.
   * @returns  Returns true if authentication is enabled, false otherwise.
   */
  public isAuthEnabled(): boolean {
    return this.config.authType() !== 'anonymous';
  }

  public getPreferencesLink(): string {
    if (this.config.authType() === 'keycloakjs') {
      const keycloakSvc = this.authService as KeycloakAuthenticationService;
      return keycloakSvc.getRealmUrl() + '/account/?referrer=microcks-app-js';
    }
    return '';
  }

  public logout(): void {
    this.authService.logout();
  }

  public hasRole(role: string): boolean {
    return this.authService.hasRole(role);
  }

  public canUseMicrocksHub(): boolean {
    if (this.hasFeatureEnabled('microcks-hub')) {
      const rolesStr = this.config.getFeatureProperty(
        'microcks-hub',
        'allowed-roles'
      );
      if (rolesStr == undefined || rolesStr === '') {
        return true;
      }
      // If roles specified, check if any is endorsed.
      const roles = rolesStr.split(',');
      for (const role of roles) {
        if (this.hasRole(role)) {
          return true;
        }
      }
      return false;
    }
    // Default is false to keep behaviour of previous releases.
    return false;
  }

  public hasFeatureEnabled(feature: string): boolean {
    return this.config.hasFeatureEnabled(feature);
  }

  public openQuickImportDialog(): void {
    this.uploaderDialogService.openArtifactUploader();
  }

  public themeIcon(): string {
    return document.body.classList.contains('dark') ? 'fa fa-sun-o' : 'fa fa-moon-o';
  }

  public toggleDarkMode(): void {
    if (document.body.classList.contains('dark')) {
      document.body.classList.remove('dark');
      document.body.classList.add('light');
      this.hljsLoader.setTheme('assets/styles/github.min.css');
      localStorage.setItem('microcks-theme', 'light');
    } else {
      document.body.classList.remove('light');
      document.body.classList.add('dark');
      this.hljsLoader.setTheme('assets/styles/github-dark.min.css');
      localStorage.setItem('microcks-theme', 'dark');
    }
  }
}
