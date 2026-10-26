package com.forA.chatbot.auth.converter;

import com.forA.chatbot.auth.dto.AuthResponse;
import com.forA.chatbot.auth.dto.RefreshTokenResponse;
import com.forA.chatbot.user.domain.User;
import org.springframework.stereotype.Component;

@Component
public class AuthConverter {

  public AuthResponse toAuthResponse(
      String accessToken,
      String refreshToken,
      Long expiresIn,
      User user,
      boolean isNewUser,
      boolean onboardingCompleted,
      boolean isRestoredUser) {
    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .expiresIn(expiresIn)
        .userId(user.getId())
        .isNewUser(isNewUser)
        .onboardingCompleted(onboardingCompleted)
        .isRestoredUser(isRestoredUser)
        .build();
  }

  public RefreshTokenResponse toRefreshTokenResponse(
      String accessToken, String refreshToken, User user) {
    boolean hasNickname = user.getNickname() != null && !user.getNickname().isEmpty();
    return RefreshTokenResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .hasNickname(hasNickname)
        .nickname(user.getNickname())
        .build();
  }
}
