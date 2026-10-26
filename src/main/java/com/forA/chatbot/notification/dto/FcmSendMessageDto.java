package com.forA.chatbot.notification.dto;

import java.util.Map;

public record FcmSendMessageDto(
    String token,
    String title,
    String body,
    Map<String, String> data) {

  public FcmSendMessageDto {
    data = data != null ? Map.copyOf(data) : Map.of();
  }
}
