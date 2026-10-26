package com.forA.chatbot.auth.jwt;

import com.forA.chatbot.auth.service.BlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final BlacklistService blacklistService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    log.debug("JwtAuthenticationFilter 진입 - URI: {}", request.getRequestURI());
    String token = jwtUtil.extractTokenFromRequest(request);

    if (token != null && jwtUtil.validateToken(token)) {
      // 블랙리스트 확인
      if (blacklistService.isBlacklisted(token)) {
        log.warn("블랙리스트된 토큰 접근 차단: {}", token.substring(0, 20) + "...");
        filterChain.doFilter(request, response);
        return;
      }

      Long userId = jwtUtil.getUserIdFromToken(token);
      log.info("토큰에서 꺼낸 userId: {}", userId);
      Authentication authentication =
          new UsernamePasswordAuthenticationToken(
              new CustomUserDetails(userId), null, Collections.emptyList());

      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.info("JWT 인증 성공 - userId: {}", userId);
    }

    filterChain.doFilter(request, response);
    log.debug("Authentication 설정 완료");
  }
}
