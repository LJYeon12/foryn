package com.forA.chatbot.subscriptions.dto;

import lombok.Getter;

/**
 * Apple의 'signedTransactionInfo'를 디코딩한 후의 DTO
 * 필요한 필드만 매핑
 */
@Getter
public class DecodedTransaction {
  private String originalTransactionId; // 구독 고유 식별자
  private String productId;
  private String type;
  private Long expiresDate;             // 만료일 (타임스탬프)
  private Boolean isInIntroOfferPeriod; // 무료 체험 또는 소개 할인 기간 여부
  private String status;
}
