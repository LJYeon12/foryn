package com.forA.chatbot.medications.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MedicationResponseDto {

  private Long id;
  private String name;
  private List<String> typeTags;
  private List<String> takeDays;
  private String takeTime;
  private LocalDateTime createdAt;

  @Builder
  public MedicationResponseDto(
      Long id,
      String name,
      List<String> typeTags,
      List<String> takeDays,
      String takeTime,
      LocalDateTime createdAt) {
    this.id = id;
    this.name = name;
    this.typeTags = typeTags;
    this.takeDays = takeDays;
    this.takeTime = takeTime;
    this.createdAt = createdAt;
  }
}
