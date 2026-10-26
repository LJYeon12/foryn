package com.forA.chatbot.medications.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TodayMedicationResponseDto {
  private Long medicationId;
  private String name;
  private LocalDate date;
  @JsonFormat(pattern = "HH:mm")
  private LocalTime takeTime;

  private List<String> typeTags;

  private List<String> takeDays;

  private TodayHistory todayHistory;

  @Builder
  public TodayMedicationResponseDto(
      Long medicationId,
      String name,
      LocalDate date,
      LocalTime takeTime,
      List<String> typeTags,
      List<String> takeDays,
      TodayHistory todayHistory) {
    this.medicationId = medicationId;
    this.name = name;
    this.date = date;
    this.takeTime = takeTime;
    this.typeTags = typeTags;
    this.takeDays = takeDays;
    this.todayHistory = todayHistory;
  }

  @Getter
  public static class TodayHistory {
    private String status; // 복용 완료(TAKEN), 건너뜀(SKIPPED), 대기(PENDING)
    private Integer conditionLevel; // -2 ~ +2, null 허용

    @Builder
    public TodayHistory(String status, Integer conditionLevel) {
      this.status = status;
      this.conditionLevel = conditionLevel;
    }
  }
}
