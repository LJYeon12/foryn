package com.forA.chatbot.auth.domain;

import com.forA.chatbot.global.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blacklisted_tokens")
@NoArgsConstructor
@Getter
public class BlacklistedToken extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "token", nullable = false, length = 500)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "user_id")
  private Long userId;

  @Builder
  public BlacklistedToken(String token, LocalDateTime expiresAt, Long userId) {
    this.token = token;
    this.expiresAt = expiresAt;
    this.userId = userId;
  }
}
