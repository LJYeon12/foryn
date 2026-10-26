package com.forA.chatbot.notification.dto;

import com.forA.chatbot.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FcmNotificationRequestDto {
  private String deviceToken;
  private NotificationType notificationType;
  private String title;
  private String body;
}