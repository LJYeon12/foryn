package com.forA.chatbot.subscriptions.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 애플 웹훅의 'signedPayload' JWS를 디코딩한 실제 데이터 객체
 * https://developer.apple.com/documentation/appstoreservernotifications/responsebodyv2decodedpayload
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecodedNotificationPayload {
  private String notificationType; // 알림 유형 (예: "DID_RENEW", "REFUND", "REVOKE")
  private String subtype;
  private String notificationUUID; // 알림 고유 ID
  private NotificationDataPayload data; // 실제 트랜잭션 정보와 갱신 정보가 담긴 객체
}
