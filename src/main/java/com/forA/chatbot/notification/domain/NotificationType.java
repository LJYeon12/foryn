package com.forA.chatbot.notification.domain;

public enum NotificationType {
  MEDICATION("medication"),       // 약 복용 알림
  STREAK_ACHIEVEMENT("streak_achievement"), // 연속 복용 기록
  CHAT_REMINDER("chat_reminder"), // 챗봇 행동 리마인드 알림
  DIARY_REMINDER("diary_reminder"); // 한줄일기 작성 리마인드 알림

  private final String dataTypeValue;

  NotificationType(String dataTypeValue) {
    this.dataTypeValue = dataTypeValue;
  }

  public String getDataTypeValue() {
    return dataTypeValue;
  }
}
