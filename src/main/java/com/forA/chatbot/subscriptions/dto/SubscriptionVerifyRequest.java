package com.forA.chatbot.subscriptions.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SubscriptionVerifyRequest {
  @NotBlank(message = "Apple Transaction ID는 필수입니다.")
  private String originalTransactionId;
}
