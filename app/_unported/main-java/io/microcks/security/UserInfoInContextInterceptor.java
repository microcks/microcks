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
package io.github.microcks.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A Spring Web HandlerInterceptor that checks if @EnabledUserInfoInContext annotation is present on controller method
 * in order to inject UserInfo as a HTTP request attribute.
 * @author laurent
 */
public class UserInfoInContextInterceptor implements HandlerInterceptor {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(UserInfoInContextInterceptor.class);

   @Override
   public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
      log.trace("Intercepting and pre-handling request to check @EnableUserInfoInContext");
      if (handler instanceof HandlerMethod handlerMethod) {
         EnableUserInfoInContext needUserInfo = handlerMethod.getMethodAnnotation(EnableUserInfoInContext.class);
         if (needUserInfo == null) {
            needUserInfo = handlerMethod.getMethod().getDeclaringClass().getAnnotation(EnableUserInfoInContext.class);
         }

         // We're sure we do not need to inject UserInfo in context, so xew can proceed.
         if (needUserInfo == null) {
            return true;
         }

         log.debug("@EnableUserInfoInContext is present on {}", handler);
         SecurityContext securityContext = SecurityContextHolder.getContext();
         if (securityContext.getAuthentication() != null) {
            log.debug("Found a Spring Security Authentication to map to UserInfo");
            // Create and store UserInfo in request attribute.
            UserInfo userInfo = KeycloakTokenToUserInfoMapper.map(securityContext);
            request.setAttribute(UserInfo.class.getName(), userInfo);
         }
      }
      return HandlerInterceptor.super.preHandle(request, response, handler);
   }
}
