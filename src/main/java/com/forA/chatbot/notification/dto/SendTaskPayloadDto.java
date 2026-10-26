package com.forA.chatbot.notification.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SendTaskPayloadDto {

  private Long bundleId;
  private LocalDate scheduledDate;
  private Long scheduleVersion;

  public SendTaskPayloadDto(Long bundleId, LocalDate scheduledDate) {
    this(bundleId, scheduledDate, null);
  }
}
