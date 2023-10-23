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

import io.github.microcks.util.ObjectMapperFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Allows the configuration of the ObjectMapperFactory at startup time of Microcks application.
 * @author laurent
 */
@Configuration
public class ObjectMapperFactoryConfiguration {

   @Value("${spring.servlet.multipart.max-file-size}")
   private String maxUploadedFileSize;

   @PostConstruct
   public void configureObjectMapperFactory() {
      ObjectMapperFactory.configureMaxFileSize(maxUploadedFileSize);
   }
}