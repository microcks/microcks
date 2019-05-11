import { Component, OnInit, TemplateRef, ViewEncapsulation } from '@angular/core';
import { Observable, from, forkJoin } from 'rxjs';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { AboutModalConfig } from 'patternfly-ng/modal/about-modal/about-modal-config';
import { AboutModalEvent } from 'patternfly-ng/modal/about-modal/about-modal-event';

import { IAuthenticationService } from "../../services/auth.service";
import { VersionInfoService } from '../../services/versioninfo.service';
import { User } from "../../models/user.model";


// Thanks to https://github.com/onokumus/metismenu/issues/110#issuecomment-317254128
//import * as $ from 'jquery';
declare let $: any;

@Component({
  selector: 'vertical-nav',
  encapsulation: ViewEncapsulation.None,
  templateUrl: './vertical-nav.component.html',
  styleUrls: ['./vertical-nav.component.css']
})
export class VerticalNavComponent implements OnInit {
  aboutConfig: AboutModalConfig;
  modalRef: BsModalRef;

  constructor(protected authService: IAuthenticationService, private modalService: BsModalService,
    private versionInfoSvc: VersionInfoService) {
  }

  ngOnInit() {
    this.user().subscribe( currentUser => {
      this.versionInfoSvc.getVersionInfo().subscribe( versionInfo => {
        this.aboutConfig = {
          additionalInfo: 'Microcks is Open Source mocking and testing platform for API and microservices. Visit http://microcks.github.io for more information.',
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
}
