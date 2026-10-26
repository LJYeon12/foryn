package com.forA.chatbot.subscriptions.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DecodedNotificationPayload 내의 "data" 필드 객체
 * https://developer.apple.com/documentation/appstoreservernotifications/data
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationDataPayload {
  private String bundleId;
  private String environment;
  private String signedTransactionInfo;
  private String signedRenewalInfo;
}
