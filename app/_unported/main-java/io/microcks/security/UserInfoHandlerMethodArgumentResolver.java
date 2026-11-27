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
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * A Spring Web HandlerMethodArgumentResolver that knows how to retrieve a UserInfo controller method argument.
 * @author laurent
 */
public class UserInfoHandlerMethodArgumentResolver implements HandlerMethodArgumentResolver {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(UserInfoHandlerMethodArgumentResolver.class);

   @Override
   public boolean supportsParameter(MethodParameter methodParameter) {
      return methodParameter.getParameterType().equals(UserInfo.class);
   }

   @Override
   public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer modelAndViewContainer,
         NativeWebRequest nativeWebRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {

      // Check if already present.
      Object userInfoObj = nativeWebRequest.getAttribute(UserInfo.class.getName(), RequestAttributes.SCOPE_REQUEST);

      if (userInfoObj != null) {
         log.debug("UserInfo is already present into request attribute");
         return UserInfo.class.cast(userInfoObj);
      }

      log.debug("Creating a new UserInfo to resolve {} argument", methodParameter.getMethod());
      UserInfo userInfo = null;

      SecurityContext securityContext = SecurityContextHolder.getContext();
      if (securityContext.getAuthentication() != null) {
         log.debug("Found a Spring Security Authentication to map to UserInfo");
         userInfo = KeycloakTokenToUserInfoMapper.map(securityContext);
         nativeWebRequest.setAttribute(UserInfo.class.getName(), userInfo, RequestAttributes.SCOPE_REQUEST);
      }

      return userInfo;
   }
}
