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
package io.github.microcks.config;

import io.github.microcks.security.UserInfoHandlerMethodArgumentResolver;
import io.github.microcks.security.UserInfoInContextInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author laurent
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

   @Override
   public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(new UserInfoInContextInterceptor()).addPathPatterns("/api/services/**");
   }

   @Override
   public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
      argumentResolvers.add(new UserInfoHandlerMethodArgumentResolver());
   }
}
