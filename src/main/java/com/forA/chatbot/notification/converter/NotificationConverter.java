package com.forA.chatbot.notification.converter;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.dto.FcmSendMessageDto;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationConverter {

  public FcmNotificationRequestDto toChatReminderRequest(DeviceToken deviceToken, String nickname) {
    return FcmNotificationRequestDto.builder()
        .deviceToken(deviceToken.getDeviceToken())
        .notificationType(NotificationType.CHAT_REMINDER)
        .title(String.format("%s님이 pick한 행동하고 오셨나요?🎯", nickname))
        .body("포린이가 간절하게 기다리고 있어냥😻")
        .build();
  }

  public FcmNotificationRequestDto toStreakAchievementRequest(DeviceToken deviceToken) {
    return FcmNotificationRequestDto.builder()
        .deviceToken(deviceToken.getDeviceToken())
        .notificationType(NotificationType.STREAK_ACHIEVEMENT)
        .title("💊3일 연속 기록 성공!")
        .body("이건 쉬운 게 아니야 대단해냥🐾")
        .build();
  }

  public FcmNotificationRequestDto toDiaryReminderRequest(DeviceToken deviceToken) {
    return FcmNotificationRequestDto.builder()
        .deviceToken(deviceToken.getDeviceToken())
        .notificationType(NotificationType.DIARY_REMINDER)
        .title("한줄일기 쓸 시간이에요")
        .body("지금 마음을 포린이에게 짧게 들려주세요🐾")
        .build();
  }

  public FcmSendMessageDto toFcmSendMessage(FcmNotificationRequestDto request) {
    return new FcmSendMessageDto(
        request.getDeviceToken(),
        request.getTitle(),
        request.getBody(),
        Map.of("type", request.getNotificationType().getDataTypeValue()));
  }

  public FcmNotificationRequestDto toMedicationRequest(DeviceToken deviceToken,
      MedicationBundle bundle) {
    return FcmNotificationRequestDto.builder()
        .deviceToken(deviceToken.getDeviceToken())
        .notificationType(NotificationType.MEDICATION)
        .title(String.format("%s💊 먹을 시간이에요!", bundle.getBundleName()))
        .body("약 먹는 거 잊으면 안된다옹")
        .build();
  }
}
