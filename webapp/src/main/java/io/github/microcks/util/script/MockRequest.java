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
package io.github.microcks.util.script;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A serializable representation of an HttpServletRequest that captures all its final values. This class is designed to
 * be easily serialized with Jackson and provides a comprehensive snapshot of request data for testing, logging, or
 * analysis purposes.
 *
 * @author Andrea
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MockRequest {

   @JsonProperty("method")
   private String method;

   @JsonProperty("requestURI")
   private String requestURI;

   @JsonProperty("requestURL")
   private String requestURL;

   @JsonProperty("contextPath")
   private String contextPath;

   @JsonProperty("servletPath")
   private String servletPath;

   @JsonProperty("pathInfo")
   private String pathInfo;

   @JsonProperty("queryString")
   private String queryString;

   @JsonProperty("scheme")
   private String scheme;

   @JsonProperty("serverName")
   private String serverName;

   @JsonProperty("serverPort")
   private int serverPort;

   @JsonProperty("remoteAddr")
   private String remoteAddr;

   @JsonProperty("remoteHost")
   private String remoteHost;

   @JsonProperty("remotePort")
   private int remotePort;

   @JsonProperty("localAddr")
   private String localAddr;

   @JsonProperty("localName")
   private String localName;

   @JsonProperty("localPort")
   private int localPort;

   @JsonProperty("protocol")
   private String protocol;

   @JsonProperty("characterEncoding")
   private String characterEncoding;

   @JsonProperty("contentType")
   private String contentType;

   @JsonProperty("contentLength")
   private long contentLength;

   @JsonProperty("locale")
   private String locale;

   @JsonProperty("locales")
   private String[] locales;

   @JsonProperty("headers")
   private Map<String, String[]> headers;

   @JsonProperty("parameters")
   private Map<String, String[]> parameters;

   @JsonProperty("cookies")
   private Cookie[] cookies;

   @JsonProperty("attributes")
   private Map<String, Object> attributes;

   @JsonProperty("sessionId")
   private String sessionId;

   @JsonProperty("isSecure")
   private boolean isSecure;

   @JsonProperty("isRequestedSessionIdValid")
   private boolean isRequestedSessionIdValid;

   @JsonProperty("isRequestedSessionIdFromCookie")
   private boolean isRequestedSessionIdFromCookie;

   @JsonProperty("isRequestedSessionIdFromURL")
   private boolean isRequestedSessionIdFromURL;

   @JsonProperty("userPrincipal")
   private String userPrincipal;

   @JsonProperty("authType")
   private String authType;

   /**
    * Default constructor for Jackson deserialization.
    */
   public MockRequest() {
   }

   /**
    * Create a MockRequest from an HttpServletRequest, capturing all its final values.
    *
    * @param request The HttpServletRequest to capture
    */
   public MockRequest(HttpServletRequest request) {
      // Return early if request is null to avoid NullPointerException
      if (request == null) {
         return;
      }

      // Capture basic request information that should always be available
      this.method = request.getMethod();
      this.requestURI = request.getRequestURI();
      // RequestURL might be null in some servlet containers
      this.requestURL = request.getRequestURL() != null ? request.getRequestURL().toString() : null;
      this.contextPath = request.getContextPath();
      this.servletPath = request.getServletPath();
      this.pathInfo = request.getPathInfo();
      this.queryString = request.getQueryString();
      this.scheme = request.getScheme();
      this.serverName = request.getServerName();
      this.serverPort = request.getServerPort();

      // Capture remote client information
      this.remoteAddr = request.getRemoteAddr();
      this.remoteHost = request.getRemoteHost();
      this.remotePort = request.getRemotePort();

      // Capture local server information
      this.localAddr = request.getLocalAddr();
      this.localName = request.getLocalName();
      this.localPort = request.getLocalPort();

      // Capture request metadata
      this.protocol = request.getProtocol();
      this.characterEncoding = request.getCharacterEncoding();
      this.contentType = request.getContentType();
      this.contentLength = request.getContentLengthLong();

      // Extract complex properties using helper methods
      this.locale = extractLocale(request); // Locale might not be available
      this.locales = extractLocales(request); // Multiple locales might not be available
      this.headers = extractHeaders(request); // Headers might be empty or null
      this.parameters = extractParameters(request); // Parameters might be empty or null
      this.cookies = request.getCookies(); // Cookies might be null if none present
      this.attributes = extractAttributes(request); // Attributes might be empty or null

      // Extract session and security information
      this.sessionId = extractSessionId(request); // Session might not exist
      this.isSecure = request.isSecure();
      this.isRequestedSessionIdValid = request.isRequestedSessionIdValid();
      this.isRequestedSessionIdFromCookie = request.isRequestedSessionIdFromCookie();
      this.isRequestedSessionIdFromURL = request.isRequestedSessionIdFromURL();
      this.userPrincipal = extractUserPrincipal(request); // Principal might not be available
      this.authType = request.getAuthType(); // Auth type might be null if not authenticated
   }

   /**
    * Extracts the primary locale from the request, handling null case.
    */
   private String extractLocale(HttpServletRequest request) {
      Locale requestLocale = request.getLocale();
      return requestLocale != null ? requestLocale.toString() : null;
   }

   /**
    * Extracts all locales from the request, filtering null values.
    */
   private String[] extractLocales(HttpServletRequest request) {
      Enumeration<Locale> requestLocales = request.getLocales();
      if (requestLocales == null || !requestLocales.hasMoreElements()) {
         return new String[0];
      }
      java.util.List<String> localeList = new java.util.ArrayList<>();
      while (requestLocales.hasMoreElements()) {
         Locale loc = requestLocales.nextElement();
         if (loc != null) {
            localeList.add(loc.toString());
         }
      }
      return localeList.toArray(new String[0]);
   }

   /**
    * Extracts all headers from the request, handling null cases and empty values.
    */
   private Map<String, String[]> extractHeaders(HttpServletRequest request) {
      Map<String, String[]> headersMap = new HashMap<>();
      Enumeration<String> headerNames = request.getHeaderNames();
      if (headerNames == null) {
         return headersMap;
      }
      while (headerNames.hasMoreElements()) {
         String headerName = headerNames.nextElement();
         if (headerName == null) {
            continue; // Skip null header names
         }
         Enumeration<String> headerValues = request.getHeaders(headerName);
         if (headerValues == null) {
            continue; // Skip null header values
         }
         java.util.List<String> values = new java.util.ArrayList<>();
         while (headerValues.hasMoreElements()) {
            String value = headerValues.nextElement();
            if (value != null) {
               values.add(value);
            }
         }
         headersMap.put(headerName, values.toArray(new String[0]));
      }
      return headersMap;
   }

   /**
    * Extracts parameters from the request, ensuring a non-null return value.
    */
   private Map<String, String[]> extractParameters(HttpServletRequest request) {
      Map<String, String[]> paramMap = request.getParameterMap();
      return paramMap != null ? new HashMap<>(paramMap) : new HashMap<>();
   }

   /**
    * Extracts serializable attributes from the request, filtering non-serializable values.
    */
   private Map<String, Object> extractAttributes(HttpServletRequest request) {
      Map<String, Object> attributesMap = new HashMap<>();
      Enumeration<String> attributeNames = request.getAttributeNames();
      if (attributeNames == null) {
         return attributesMap;
      }
      while (attributeNames.hasMoreElements()) {
         String attrName = attributeNames.nextElement();
         if (attrName == null) {
            continue; // Skip null attribute names
         }
         Object attrValue = request.getAttribute(attrName);
         if (isSerializable(attrValue)) {
            attributesMap.put(attrName, attrValue);
         }
      }
      return attributesMap;
   }

   /**
    * Extracts session ID safely, handling the case where session doesn't exist.
    */
   private String extractSessionId(HttpServletRequest request) {
      try {
         if (request.getSession(false) != null) {
            return request.getSession(false).getId();
         }
      } catch (Exception ignored) {
         // Session might not be available, ignore
      }
      return null;
   }

   /**
    * Extracts user principal safely, handling the case where security context is not available.
    */
   private String extractUserPrincipal(HttpServletRequest request) {
      try {
         if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
         }
      } catch (Exception ignored) {
         // User principal might not be available, ignore
      }
      return null;
   }

   /**
    * Check if an object is serializable by Jackson.
    *
    * @param obj The object to check
    * @return true if the object is serializable
    */
   private boolean isSerializable(Object obj) {
      if (obj == null) {
         return true;
      }

      Class<?> clazz = obj.getClass();
      return clazz.isPrimitive() || clazz == String.class || clazz == Boolean.class || clazz == Character.class
            || Number.class.isAssignableFrom(clazz) || clazz.isArray()
            || java.util.Collection.class.isAssignableFrom(clazz) || java.util.Map.class.isAssignableFrom(clazz);
   }

   // Getters and setters for all properties
   public String getMethod() {
      return method;
   }

   public void setMethod(String method) {
      this.method = method;
   }

   public String getRequestURI() {
      return requestURI;
   }

   public void setRequestURI(String requestURI) {
      this.requestURI = requestURI;
   }

   public String getRequestURL() {
      return requestURL;
   }

   public void setRequestURL(String requestURL) {
      this.requestURL = requestURL;
   }

   public String getContextPath() {
      return contextPath;
   }

   public void setContextPath(String contextPath) {
      this.contextPath = contextPath;
   }

   public String getServletPath() {
      return servletPath;
   }

   public void setServletPath(String servletPath) {
      this.servletPath = servletPath;
   }

   public String getPathInfo() {
      return pathInfo;
   }

   public void setPathInfo(String pathInfo) {
      this.pathInfo = pathInfo;
   }

   public String getQueryString() {
      return queryString;
   }

   public void setQueryString(String queryString) {
      this.queryString = queryString;
   }

   public String getScheme() {
      return scheme;
   }

   public void setScheme(String scheme) {
      this.scheme = scheme;
   }

   public String getServerName() {
      return serverName;
   }

   public void setServerName(String serverName) {
      this.serverName = serverName;
   }

   public int getServerPort() {
      return serverPort;
   }

   public void setServerPort(int serverPort) {
      this.serverPort = serverPort;
   }

   public String getRemoteAddr() {
      return remoteAddr;
   }

   public void setRemoteAddr(String remoteAddr) {
      this.remoteAddr = remoteAddr;
   }

   public String getRemoteHost() {
      return remoteHost;
   }

   public void setRemoteHost(String remoteHost) {
      this.remoteHost = remoteHost;
   }

   public int getRemotePort() {
      return remotePort;
   }

   public void setRemotePort(int remotePort) {
      this.remotePort = remotePort;
   }

   public String getLocalAddr() {
      return localAddr;
   }

   public void setLocalAddr(String localAddr) {
      this.localAddr = localAddr;
   }

   public String getLocalName() {
      return localName;
   }

   public void setLocalName(String localName) {
      this.localName = localName;
   }

   public int getLocalPort() {
      return localPort;
   }

   public void setLocalPort(int localPort) {
      this.localPort = localPort;
   }

   public String getProtocol() {
      return protocol;
   }

   public void setProtocol(String protocol) {
      this.protocol = protocol;
   }

   public String getCharacterEncoding() {
      return characterEncoding;
   }

   public void setCharacterEncoding(String characterEncoding) {
      this.characterEncoding = characterEncoding;
   }

   public String getContentType() {
      return contentType;
   }

   public void setContentType(String contentType) {
      this.contentType = contentType;
   }

   public long getContentLength() {
      return contentLength;
   }

   public void setContentLength(long contentLengthLong) {
      this.contentLength = contentLengthLong;
   }

   public String getLocale() {
      return locale;
   }

   public void setLocale(String locale) {
      this.locale = locale;
   }

   public String[] getLocales() {
      return locales;
   }

   public void setLocales(String[] locales) {
      this.locales = locales;
   }

   public Map<String, String[]> getHeaders() {
      return headers;
   }

   public void setHeaders(Map<String, String[]> headers) {
      this.headers = headers;
   }

   public Map<String, String[]> getParameters() {
      return parameters;
   }

   public void setParameters(Map<String, String[]> parameters) {
      this.parameters = parameters;
   }

   public Cookie[] getCookies() {
      return cookies;
   }

   public void setCookies(Cookie[] cookies) {
      this.cookies = cookies;
   }

   public Map<String, Object> getAttributes() {
      return attributes;
   }

   public void setAttributes(Map<String, Object> attributes) {
      this.attributes = attributes;
   }

   public String getSessionId() {
      return sessionId;
   }

   public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }

   public boolean isSecure() {
      return isSecure;
   }

   public void setSecure(boolean secure) {
      isSecure = secure;
   }

   public boolean isRequestedSessionIdValid() {
      return isRequestedSessionIdValid;
   }

   public void setRequestedSessionIdValid(boolean requestedSessionIdValid) {
      isRequestedSessionIdValid = requestedSessionIdValid;
   }

   public boolean isRequestedSessionIdFromCookie() {
      return isRequestedSessionIdFromCookie;
   }

   public void setRequestedSessionIdFromCookie(boolean requestedSessionIdFromCookie) {
      isRequestedSessionIdFromCookie = requestedSessionIdFromCookie;
   }

   public boolean isRequestedSessionIdFromURL() {
      return isRequestedSessionIdFromURL;
   }

   public void setRequestedSessionIdFromURL(boolean requestedSessionIdFromURL) {
      isRequestedSessionIdFromURL = requestedSessionIdFromURL;
   }

   public String getUserPrincipal() {
      return userPrincipal;
   }

   public void setUserPrincipal(String userPrincipal) {
      this.userPrincipal = userPrincipal;
   }

   public String getAuthType() {
      return authType;
   }

   public void setAuthType(String authType) {
      this.authType = authType;
   }
}
