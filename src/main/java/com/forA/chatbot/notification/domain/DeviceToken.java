package com.forA.chatbot.notification.domain;

import com.forA.chatbot.global.BaseTimeEntity;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "device_tokens")
public class DeviceToken extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "device_token", nullable = false, unique = true)
  private String deviceToken;

  @Column(name = "is_push_enabled", nullable = false)
  private boolean isPushEnabled = true;

  @Builder // 빌더 패턴 적용
  public DeviceToken(User user, String deviceToken, boolean isPushEnabled) {
    this.user = user;
    this.deviceToken = deviceToken;
    this.isPushEnabled = isPushEnabled;
  }

  // 비즈니스 로직: 푸시 설정 업데이트
  public void updatePushEnabled(boolean isPushEnabled) {
    this.isPushEnabled = isPushEnabled;
  }

  public void updateUser(User user) {
    this.user = user;
  }
}
