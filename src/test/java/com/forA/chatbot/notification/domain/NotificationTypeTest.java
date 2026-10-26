package com.forA.chatbot.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotificationTypeTest {

  @ParameterizedTest
  @CsvSource({
    "MEDICATION, medication",
    "CHAT_REMINDER, chat_reminder",
    "STREAK_ACHIEVEMENT, streak_achievement",
    "DIARY_REMINDER, diary_reminder"
  })
  @DisplayName("각 NotificationType은 FCM data payload용 타입 문자열을 반환한다")
  void getDataTypeValue_shouldReturnCorrectFcmTypeString(String typeName, String expected) {
    NotificationType type = NotificationType.valueOf(typeName);

    assertThat(type.getDataTypeValue()).isEqualTo(expected);
  }

  @Test
  @DisplayName("모든 NotificationType은 null이 아닌 dataTypeValue를 가진다")
  void allTypes_shouldHaveNonNullDataTypeValue() {
    for (NotificationType type : NotificationType.values()) {
      assertThat(type.getDataTypeValue())
          .as("NotificationType.%s의 dataTypeValue가 null이면 안 됩니다", type.name())
          .isNotNull()
          .isNotBlank();
    }
  }
}
