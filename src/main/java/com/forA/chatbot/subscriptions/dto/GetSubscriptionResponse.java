package com.forA.chatbot.subscriptions.dto;

import com.forA.chatbot.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GetSubscriptionResponse {
  private String productId;
  private LocalDateTime expiresAt;
  private SubscriptionStatus status;
  private boolean isTrial;
  private boolean autoRenew;
  private String originalTransactionId;
}
