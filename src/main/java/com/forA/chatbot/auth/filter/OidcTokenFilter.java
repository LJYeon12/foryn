package com.forA.chatbot.auth.filter;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class OidcTokenFilter extends OncePerRequestFilter {

  private static final String INTERNAL_PATH_PREFIX = "/internal/";

  private final String expectedAudience;
  private final String expectedSaEmail;

  public OidcTokenFilter(
      @Value("${cloud-tasks.target-base-url:}") String expectedAudience,
      @Value("${cloud-tasks.task-execution-sa-email:}") String expectedSaEmail) {
    this.expectedAudience = expectedAudience;
    this.expectedSaEmail = expectedSaEmail;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing OIDC token");
      return;
    }

    String token = authHeader.substring(7);

    JsonWebSignature jws;
    try {
      TokenVerifier tokenVerifier = TokenVerifier.newBuilder()
          .setAudience(expectedAudience)
          .setIssuer("https://accounts.google.com")
          .build();

      jws = tokenVerifier.verify(token);
    } catch (TokenVerifier.VerificationException | IllegalArgumentException e) {
      log.warn("Internal OIDC rejected: reason=invalid_token");
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid OIDC token");
      return;
    }

    Object email = jws.getPayload().get("email");
    if (!expectedSaEmail.equals(email)) {
      log.warn("Internal OIDC rejected: reason=service_account_mismatch");
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized service account");
      return;
    }

    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        email, null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
    filterChain.doFilter(request, response);
  }
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String path = requestUri;

    if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
      path = requestUri.substring(contextPath.length());
    }

    return !path.startsWith(INTERNAL_PATH_PREFIX);
  }
}
