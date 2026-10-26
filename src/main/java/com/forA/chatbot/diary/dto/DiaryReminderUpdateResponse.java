package com.forA.chatbot.diary.dto;

import java.util.List;

public record DiaryReminderUpdateResponse(
    List<Reminder> reminders,
    int maxCount
) {

  public record Reminder(
      String time,
      boolean enabled
  ) {
  }
}
