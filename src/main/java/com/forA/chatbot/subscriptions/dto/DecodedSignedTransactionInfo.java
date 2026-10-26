package com.forA.chatbot.subscriptions.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

// https://developer.apple.com/documentation/appstoreserverapi/jwstransactiondecodedpayload
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecodedSignedTransactionInfo {
  private String originalTransactionId; // 원본 거래 ID (사용자 식별) - 사용자 고유 구독 ID
  private String transactionId; // 현재 거래 ID
  private String productId;
  private Long expiresDate;
  private String type;
  private Integer offerType; // 1: 소개 오퍼, 2: 프로모션, 3: 오퍼 코드
  private String offerIdentifier; // ASC에서 설정한 오퍼 ID (예: "FREE_TRIAL")
  private Integer status; // (전체 구독 상태 조회 API의 status와 동일)
  private String bundleId;
  private Long purchaseDate;
  private Long originalPurchaseDate;

  public LocalDateTime getExpiresDateAsLocalDateTime() {
    if (this.expiresDate == null) {
      return null;
    }
    return Instant.ofEpochMilli(this.expiresDate).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }

  // 이 거래가 무료 체험인지 확인하는 헬퍼 메서드
  public boolean isTrial() {
    return this.offerType != null && this.offerType == 1
        && "FREE_TRIAL".equals(this.offerIdentifier);
  }

}
