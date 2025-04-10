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
import { Injectable } from '@angular/core';
import { HttpEvent, HttpInterceptor, HttpHandler, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IAuthenticationService } from './auth.service';

@Injectable()
export class AuthenticationHttpInterceptor implements HttpInterceptor {

  constructor(protected authService: IAuthenticationService) { }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    //console.log('[AuthenticationHttpInterceptor] intercept for ' + req.method);

    if (req.method === 'OPTIONS') {
      return next.handle(req);
    }

    // Build new set of headers for authentication purpose.
    if (this.authService.isAuthenticated()) {
      const authHeaders: {[header: string]: string} = {};
      this.authService.injectAuthHeaders(authHeaders);
      const changedReq = req.clone({setHeaders: authHeaders});
      return next.handle(changedReq);
    }

    return next.handle(req);
  }
}
