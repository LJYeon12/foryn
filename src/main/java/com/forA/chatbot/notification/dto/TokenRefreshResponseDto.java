package com.forA.chatbot.notification.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TokenRefreshResponseDto {
  private String refreshedToken;
}
