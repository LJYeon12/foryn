package com.forA.chatbot.subscriptions.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionStatusResponse {
  private String environment;
  private Long appAppleId;
  private String bundleId;
  private List<SubscriptionGroupData> data;

  @NoArgsConstructor
  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SubscriptionGroupData {
    private String subscriptionGroupIdentifier;
    private List<LastTransaction> lastTransactions;
  }

  @NoArgsConstructor
  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LastTransaction {
    private String originalTransactionId;
    private Integer status; // 구독 상태 (1: 활성, 2: 만료 등)
    private String signedRenewalInfo; // 갱신 정보 JWS
    private String signedTransactionInfo; // 거래 내역 JWS
  }
}
