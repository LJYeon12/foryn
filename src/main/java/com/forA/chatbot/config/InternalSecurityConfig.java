package com.forA.chatbot.config;

import com.forA.chatbot.auth.filter.OidcTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class InternalSecurityConfig {

  private final OidcTokenFilter oidcTokenFilter;

  @Bean
  public FilterRegistrationBean<OidcTokenFilter> oidcTokenFilterRegistration() {
    FilterRegistrationBean<OidcTokenFilter> registration =
        new FilterRegistrationBean<>(oidcTokenFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/internal/**")
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .addFilterBefore(oidcTokenFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
