package com.forA.chatbot.user.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserProfileResponse {

  private final Long userId;
  private final String nickname;
  private final String gender;
  private final Integer birthYear;
  private final List<String> job;
  private final List<String> mentalConditions;
  private final Map<String, List<String>> symptoms;
  // user/me 응답에서 사용자 시간대와 한줄일기 알림 설정을 함께 전달하기 위한 확장 필드다.
  private final String timeZone;
  private final List<DiaryReminder> diaryReminders;
  private final int diaryReminderMaxCount;

  // API 문서의 diaryReminders[] 형식에 맞춰 HH:mm 시간과 활성화 여부만 노출한다.
  public record DiaryReminder(
      String time,
      boolean enabled
  ) {
  }
}
