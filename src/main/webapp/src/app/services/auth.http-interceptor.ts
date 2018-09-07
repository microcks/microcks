
import { Injectable } from '@angular/core';
import { HttpEvent, HttpInterceptor, HttpHandler, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IAuthenticationService } from "./auth.service";

@Injectable()
export class AuthenticationHttpInterceptor implements HttpInterceptor {

  constructor(protected authService: IAuthenticationService) { }

  //constructor() { }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    console.log('[AuthenticationHttpInterceptor] intercept for ' + req.method);
    
    if (req.method === 'OPTIONS') {
      return next.handle(req);
    }
    
    // Build new set of headers for authentication purpose.
    if (this.authService.isAuthenticated) {
      var authHeaders: {[header: string]: string} = {};
      this.authService.injectAuthHeaders(authHeaders);
      const changedReq = req.clone({setHeaders: authHeaders});
      return next.handle(changedReq);
    }
    
    return next.handle(req);
  }
}