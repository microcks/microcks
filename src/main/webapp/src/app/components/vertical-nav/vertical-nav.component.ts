import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';

import { IAuthenticationService } from "../../services/auth.service";
import { User } from "../../models/user.model";

// Thanks to https://github.com/onokumus/metismenu/issues/110#issuecomment-317254128
//import * as $ from 'jquery';
declare let $: any;

@Component({
  selector: 'vertical-nav',
  templateUrl: './vertical-nav.component.html',
  styleUrls: ['./vertical-nav.component.css']
})
export class VerticalNavComponent implements OnInit {

  constructor(protected authService: IAuthenticationService) {
  }

  ngOnInit() {
  }

  ngAfterViewInit() {
    $().setupVerticalNavigation(true);
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
