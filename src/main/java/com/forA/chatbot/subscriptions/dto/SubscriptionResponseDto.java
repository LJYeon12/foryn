package com.forA.chatbot.subscriptions.dto;

import com.forA.chatbot.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionResponseDto {
  private Long subscriptionId;
  private String productId;
  private SubscriptionStatus status; // 활성(ACTIVE), 만료(EXPIRED), 취소(CANCELLED)
  private LocalDateTime expiresAt; // 만료일
  private boolean isTrial; // 무료 체험 여부
}
