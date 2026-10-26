package com.forA.chatbot.medications.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MedicationLogDto {
  private Long medicationId;
  private String date;
  private String status;
  private Integer conditionLevel;

  @Builder
  public MedicationLogDto(Long medicationId, String date, String status, Integer conditionLevel) {
    this.medicationId = medicationId;
    this.date = date;
    this.status = status;
    this.conditionLevel = conditionLevel;
  }
}
