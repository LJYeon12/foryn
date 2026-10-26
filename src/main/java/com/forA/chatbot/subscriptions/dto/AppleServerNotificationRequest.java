package com.forA.chatbot.subscriptions.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AppleServerNotificationRequest {
  private String signedPayload;
}
