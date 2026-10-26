package com.forA.chatbot.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TokenRefreshRequestDto {
  @NotBlank(message = "토큰은 필수입니다.")
  private String token;
  private Boolean isPushEnabled;
}
