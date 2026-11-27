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
package io.github.microcks.util;

import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.ParameterLocation;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Utility class for holding various case around ParameterConstraints.
 * @author laurent
 */
public class ParameterConstraintUtil {

   /** Private constructor to hide the implicit public one and prevent instantiation. */
   private ParameterConstraintUtil() {
      // Hidden constructor
   }

   /**
    * Validate that a parameter constraint it respected or violated. Return a message if violated.
    * @param request    HttpServlet request holding parameters to validate
    * @param constraint Constraint to apply to one request parameter.
    * @return A string representing constraint violation if any. null otherwise.
    */
   public static String validateConstraint(HttpServletRequest request, ParameterConstraint constraint) {
      String value = null;
      if (ParameterLocation.header == constraint.getIn()) {
         value = request.getHeader(constraint.getName());
      } else if (ParameterLocation.query == constraint.getIn()) {
         value = request.getParameter(constraint.getName());
      } else if (ParameterLocation.cookie == constraint.getIn()) {
         value = getCookieValue(request, constraint.getName());
      }

      if (value != null) {
         if (constraint.getMustMatchRegexp() != null && !Pattern.matches(constraint.getMustMatchRegexp(), value)) {
            return "Parameter " + constraint.getName() + " should match " + constraint.getMustMatchRegexp();
         }
      } else {
         if (constraint.isRequired()) {
            return "Parameter " + constraint.getName() + " is required";
         }
      }
      return null;
   }

   /**
    * Extract cookie value from HttpServletRequest by name.
    * @param request    HttpServletRequest to extract cookie from
    * @param cookieName Name of the cookie to retrieve
    * @return Cookie value if found, null otherwise
    */
   private static String getCookieValue(HttpServletRequest request, String cookieName) {
      Cookie[] cookies = request.getCookies();
      if (cookies != null) {
         for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
               return cookie.getValue();
            }
         }
      }
      return null;
   }
}
