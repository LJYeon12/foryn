package com.forA.chatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**") // 모든 API 경로에 대해
        .allowedOrigins(
            "http://localhost:3000", // 로컬 프론트엔드 개발 서버
            "https://dev.mori.ai.kr" // 배포된 프론트엔드 도메인
            )
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 허용할 HTTP 메서드
        .allowedHeaders("*") // 모든 헤더 허용
        .allowCredentials(true) // 쿠키/인증 정보 포함 허용
        .maxAge(3600); // 사전 요청 캐시 시간 (초)
  }
}
