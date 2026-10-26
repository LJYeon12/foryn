package com.forA.chatbot.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse { // 애플 로그인 성공 후 클라이언트에 반환하는 응답

  private String accessToken;
  private String refreshToken;
  private String tokenType; // 토큰 유형: Bearer
  private Long expiresIn; // 토큰 만료 시간
  private Long userId;
  private boolean isNewUser;
  private boolean onboardingCompleted;
  private boolean isRestoredUser; // 탈퇴 후 재가입 유저 여부
}