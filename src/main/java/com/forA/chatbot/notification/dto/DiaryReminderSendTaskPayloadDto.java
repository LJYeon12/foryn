package com.forA.chatbot.notification.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiaryReminderSendTaskPayloadDto {

  private Long reminderId;
  private LocalDate scheduledDate;
}
