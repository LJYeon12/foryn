package com.forA.chatbot.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private static final RequestMatcher INTERNAL_PROMETHEUS_MATCHER =
      request -> isInternalPrometheusRequest(request);

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final Environment environment;

  @Bean
  @Order(2)
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(withDefaults()) // WebConfig에서 설정한 CORS 정책을 따르도록 설정
        .authorizeHttpRequests(
            auth -> {
              auth
                  .requestMatchers(HttpMethod.POST, "/api/v1/auth/apple", "/api/v1/auth/refresh")
                  .permitAll()
                  // 인증 불필요 (permitAll)
                  .requestMatchers(
                      "/",
                      "/test/**",
                      "/actuator/health",
                      "/actuator/health/**",
                      "/api/v1/subscription/notification", // 애플 웹훅 수신 경로
                      "/api/sentry-test/**", // Sentry 연동 테스트 (로컬 프로필 전용)
                      "/swagger-ui/**",
                      "/v3/api-docs/**",
                      "/swagger-resources/**",
                      "/webjars/**")
                  .permitAll();

              if (environment.acceptsProfiles(Profiles.of("local"))) {
                auth.requestMatchers("/actuator/prometheus").permitAll();
              }

              auth.requestMatchers(INTERNAL_PROMETHEUS_MATCHER).permitAll();

              // 그 외 모든 요청은 인증 필요
              auth.anyRequest().authenticated();
            })
        // JWT 인증 필터 등록
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private static boolean isInternalPrometheusRequest(HttpServletRequest request) {
    return "/actuator/prometheus".equals(request.getRequestURI()) && isLoopback(request);
  }

  private static boolean isLoopback(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    return "127.0.0.1".equals(remoteAddr)
        || "::1".equals(remoteAddr)
        || "0:0:0:0:0:0:0:1".equals(remoteAddr);
  }
}
