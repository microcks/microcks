package io.github.microcks.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
public class DynamicOriginCorsFilter implements Filter {
  private final String corsAllowedOrigins;
  private final Boolean corsAllowCredentials;

  public DynamicOriginCorsFilter(String corsAllowedOrigins, Boolean corsAllowCredentials) {
    this.corsAllowedOrigins = corsAllowedOrigins;
    this.corsAllowCredentials = corsAllowCredentials;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
    throws IOException, ServletException {
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
    String origin = httpRequest.getHeader("Origin");
    if (origin == null) {
      origin = corsAllowedOrigins;
    }
    response.setHeader("Access-Control-Allow-Origin", origin);
    response.setHeader("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE");
    response.setHeader("Access-Control-Max-Age", "3600");
    response.setHeader("Access-Control-Allow-Headers", "*");
    if (corsAllowCredentials) {
      response.setHeader("Access-Control-Allow-Credentials", "true");
    }
    chain.doFilter(servletRequest, servletResponse);
  }
}
