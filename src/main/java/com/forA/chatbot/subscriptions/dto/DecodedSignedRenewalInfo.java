package com.forA.chatbot.subscriptions.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

// https://developer.apple.com/documentation/appstoreserverapi/jwsrenewalinfodecodedpayload
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecodedSignedRenewalInfo {
  // 자동 갱신 상태 (1: 켬, 0: 끔)
  private Integer autoRenewStatus;
  // 만료 사유 (1: 사용자가 취소, 2: 결제 오류 등)
  private Integer expirationIntent;
  private String originalTransactionId;
  private String productId;
}
