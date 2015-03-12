/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.lbroudoux.microcks.util;

import com.github.lbroudoux.microcks.domain.Parameter;

import java.util.List;
/**
 * Helper class for building URIs from various objects.
 * @author laurent
 */
public class URIBuilder{

   /**
    * Build a URI from a URI pattern (using {} marked variable parts) and using
    * other query parameters
    * @param pattern The URI pattern to use
    * @param parameters THe set of parameters (whether template or query based)
    * @return The instanciated URI from template and parameters
    */
   public static String buildURIFromPattern(String pattern, List<Parameter> parameters){
      // Browse parameters and choose between template or query one.
      for (Parameter parameter : parameters){
         String template = "{" + parameter.getName() + "}";
         if (pattern.contains(template)){
            // It's a template parameter.
            pattern = pattern.replace(template, parameter.getValue());
         } else {
            // It's a query parameter, ensure we have started delimiting them.
            if (!pattern.contains("?")){
               pattern += "?";
            }
            if (pattern.contains("=")){
               pattern += "&";
            }
            pattern += parameter.getName() + "=" + parameter.getValue();
         }
      }
      
      return pattern;
   }
}
