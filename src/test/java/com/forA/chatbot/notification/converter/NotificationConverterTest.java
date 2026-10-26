package com.forA.chatbot.notification.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.dto.FcmSendMessageDto;
import com.forA.chatbot.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationConverterTest {

  private NotificationConverter converter;

  @BeforeEach
  void setUp() {
    converter = new NotificationConverter();
  }

  @Nested
  @DisplayName("toFcmSendMessage")
  class ToFcmSendMessage {

    @Test
    @DisplayName("MEDICATION 타입이면 token/title/body가 그대로 담긴 FcmSendMessageDto를 반환한다")
    void medication_shouldMapAllFieldsCorrectly() {
      FcmNotificationRequestDto request =
          FcmNotificationRequestDto.builder()
              .deviceToken("test-token-abc")
              .notificationType(NotificationType.MEDICATION)
              .title("아침약💊 먹을 시간이에요!")
              .body("약 먹는 거 잊으면 안된다옹")
              .build();

      FcmSendMessageDto result = converter.toFcmSendMessage(request);

      assertThat(result.token()).isEqualTo("test-token-abc");
      assertThat(result.title()).isEqualTo("아침약💊 먹을 시간이에요!");
      assertThat(result.body()).isEqualTo("약 먹는 거 잊으면 안된다옹");
      assertThat(result.data()).containsEntry("type", "medication");
    }

    @Test
    @DisplayName("MEDICATION 타입이면 data.type = 'medication'이다")
    void medication_shouldHaveMedicationDataType() {
      FcmNotificationRequestDto request = buildRequest(NotificationType.MEDICATION);

      assertThat(converter.toFcmSendMessage(request).data())
          .containsEntry("type", "medication");
    }

    @Test
    @DisplayName("CHAT_REMINDER 타입이면 data.type = 'chat_reminder'이다")
    void chatReminder_shouldHaveChatReminderDataType() {
      FcmNotificationRequestDto request = buildRequest(NotificationType.CHAT_REMINDER);

      assertThat(converter.toFcmSendMessage(request).data())
          .containsEntry("type", "chat_reminder");
    }

    @Test
    @DisplayName("STREAK_ACHIEVEMENT 타입이면 data.type = 'streak_achievement'이다")
    void streakAchievement_shouldHaveStreakAchievementDataType() {
      FcmNotificationRequestDto request = buildRequest(NotificationType.STREAK_ACHIEVEMENT);

      assertThat(converter.toFcmSendMessage(request).data())
          .containsEntry("type", "streak_achievement");
    }

    @Test
    @DisplayName("DIARY_REMINDER 타입이면 data.type = 'diary_reminder'이다")
    void diaryReminder_shouldHaveDiaryReminderDataType() {
      FcmNotificationRequestDto request = buildRequest(NotificationType.DIARY_REMINDER);

      assertThat(converter.toFcmSendMessage(request).data())
          .containsEntry("type", "diary_reminder");
    }

    @Test
    @DisplayName("반환된 data map은 정확히 'type' 키 하나만 포함한다")
    void result_shouldContainOnlyTypeKey() {
      FcmNotificationRequestDto request = buildRequest(NotificationType.MEDICATION);

      assertThat(converter.toFcmSendMessage(request).data()).hasSize(1);
    }
  }

  @Test
  @DisplayName("한줄일기 알림 요청은 DIARY_REMINDER 타입과 포린이 문구를 가진다")
  void toDiaryReminderRequest_shouldBuildDiaryReminderRequest() {
    DeviceToken deviceToken = DeviceToken.builder()
        .user(User.builder().id(1L).email("user@example.com").build())
        .deviceToken("diary-token")
        .isPushEnabled(true)
        .build();

    FcmNotificationRequestDto result = converter.toDiaryReminderRequest(deviceToken);

    assertThat(result.getDeviceToken()).isEqualTo("diary-token");
    assertThat(result.getNotificationType()).isEqualTo(NotificationType.DIARY_REMINDER);
    assertThat(result.getTitle()).contains("한줄일기");
    assertThat(result.getBody()).contains("포린");
  }

  private FcmNotificationRequestDto buildRequest(NotificationType type) {
    return FcmNotificationRequestDto.builder()
        .deviceToken("token")
        .notificationType(type)
        .title("title")
        .body("body")
        .build();
  }
}
