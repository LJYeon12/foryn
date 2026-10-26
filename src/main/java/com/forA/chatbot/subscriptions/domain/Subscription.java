package com.forA.chatbot.subscriptions.domain;

import com.forA.chatbot.enums.SubscriptionStatus;
import com.forA.chatbot.global.BaseTimeEntity;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "product_id", nullable = false)
  private String productId;

  @Column(name = "original_transaction_id", nullable = false, unique = true)
  private String originalTransactionId;

  @Column(name = "transaction_id", nullable = false)
  private String transactionId;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(50)")
  private SubscriptionStatus status;

  @Column(name = "is_auto_renew", nullable = false)
  private Boolean isAutoRenew;

  @Column(name = "is_trial", nullable = false)
  private Boolean isTrial;

  @Builder
  public Subscription(User user, String originalTransactionId) {
    this.user = user;
    this.originalTransactionId = originalTransactionId;
    this.productId = "unknown";
    this.transactionId = "unknown";
    this.expiresAt = LocalDateTime.now().minusDays(1); // 만료 상태로 초기화
    this.status = SubscriptionStatus.EXPIRED;
    this.isAutoRenew = false;
    this.isTrial = false;
  }

  public void updateFromApple(
      String productId,
      String transactionId,
      LocalDateTime expiresAt,
      Boolean isAutoRenew,
      Boolean isTrial,
      SubscriptionStatus status) {

    this.productId = productId;
    this.transactionId = transactionId;
    this.expiresAt = expiresAt;
    this.isAutoRenew = isAutoRenew;
    this.isTrial = isTrial;
    this.status = status;
  }

  public void updateOriginalTransactionId(String newOriginalTransactionId) {
    this.originalTransactionId = newOriginalTransactionId;
  }

  public boolean isActive() {
    // 상태가 활성(ACTIVE)이고 만료일이 현재 시간 이후인지 확인
    return this.status == SubscriptionStatus.ACTIVE &&
        this.expiresAt != null &&
        this.expiresAt.isAfter(LocalDateTime.now());
  }
}
