package com.forA.chatbot.diary.dto;

import java.util.List;

public record DiaryReminderUpdateRequest(
    List<Reminder> reminders
) {

  public record Reminder(
      String time,
      Boolean enabled
  ) {
  }
}
