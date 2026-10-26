package com.forA.chatbot.subscriptions.converter;

import com.forA.chatbot.subscriptions.domain.Subscription;
import com.forA.chatbot.subscriptions.dto.GetSubscriptionResponse;
import com.forA.chatbot.subscriptions.dto.SubscriptionResponseDto;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionConverter {

  public SubscriptionResponseDto toSubscriptionResponseDto(Subscription subscription) {
    return SubscriptionResponseDto.builder()
        .subscriptionId(subscription.getId())
        .productId(subscription.getProductId())
        .status(subscription.getStatus())
        .expiresAt(subscription.getExpiresAt())
        .isTrial(subscription.getIsTrial())
        .build();
  }

  public GetSubscriptionResponse toGetSubscriptionResponse(Subscription subscription) {
    return GetSubscriptionResponse.builder()
        .productId(subscription.getProductId())
        .expiresAt(subscription.getExpiresAt())
        .status(subscription.getStatus())
        .isTrial(subscription.getIsTrial())
        .autoRenew(subscription.getIsAutoRenew())
        .originalTransactionId(subscription.getOriginalTransactionId())
        .build();
  }
}
