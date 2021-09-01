import { Component, OnInit, TemplateRef, ViewEncapsulation, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { AboutModalConfig } from 'patternfly-ng/modal/about-modal/about-modal-config';
import { AboutModalEvent } from 'patternfly-ng/modal/about-modal/about-modal-event';

import { HelpDialogComponent } from '../help-dialog/help-dialog.component';
import { IAuthenticationService } from "../../services/auth.service";
import { VersionInfoService } from '../../services/versioninfo.service';
import { User } from "../../models/user.model";
import { ConfigService } from 'src/app/services/config.service';


// Thanks to https://github.com/onokumus/metismenu/issues/110#issuecomment-317254128
//import * as $ from 'jquery';
declare let $: any;

@Component({
  selector: 'vertical-nav',
  encapsulation: ViewEncapsulation.None,
  templateUrl: './vertical-nav.component.html',
  styleUrls: ['./vertical-nav.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VerticalNavComponent implements OnInit {
  aboutConfig: AboutModalConfig;
  modalRef: BsModalRef;

  constructor(protected authService: IAuthenticationService, private modalService: BsModalService,
    private versionInfoSvc: VersionInfoService, private config: ConfigService) {
  }

  ngOnInit() {
    this.user().subscribe( currentUser => {
      this.versionInfoSvc.getVersionInfo().subscribe( versionInfo => {
        this.aboutConfig = {
          additionalInfo: 'Microcks is Open Source mocking and testing platform for API and microservices. Visit https://microcks.io for more information.',
          copyright: 'Distributed under Apache Licence v2.0',
          logoImageAlt: 'Microcks',
          logoImageSrc: 'assets/microcks.png',
          title: 'About Microcks',
          productInfo: [
            { name: 'Version', value: versionInfo.versionId },
            { name: 'Build timestamp', value: versionInfo.buildTimestamp },
            { name: 'User Login', value: currentUser.login },
            { name: 'User Name', value: currentUser.name } ]
        } as AboutModalConfig;
      });
    });
  }

  ngAfterViewInit() {
    $().setupVerticalNavigation(true);
  }

  public openHelpDialog() {
    const initialState = {};
    this.modalRef = this.modalService.show(HelpDialogComponent, {initialState});
  }

  public openAboutModal(template: TemplateRef<any>): void {
    this.modalRef = this.modalService.show(template);
  }
  public closeAboutModal($event: AboutModalEvent): void {
    this.modalRef.hide();
  }

  public user(): Observable<User> {
    return this.authService.getAuthenticatedUser();
  }

  public logout(): void {
    this.authService.logout();
  }

  public hasRole(role: string): boolean {
    return this.authService.hasRole(role);
  }

  public canUseMicrocksHub(): boolean {
    if (this.hasFeatureEnabled('microcks-hub')) {
      let rolesStr = this.config.getFeatureProperty('microcks-hub', 'allowed-roles');
      if (rolesStr == undefined || rolesStr === '') {
        return true;
      }
      // If roles specified, check if any is endorsed.
      let roles = rolesStr.split(',');
      for (let i=0; i<roles.length; i++) {
        if (this.hasRole(roles[i])) {
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
}
