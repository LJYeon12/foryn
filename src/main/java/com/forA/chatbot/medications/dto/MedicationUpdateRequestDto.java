package com.forA.chatbot.medications.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MedicationUpdateRequestDto {

  @Size(max = 20, message = "약 이름은 최대 20자까지 입력 가능합니다")
  @Pattern(regexp = ".*\\S.*", message = "약 이름은 필수입니다.")
  private String name;

  private List<String> typeTags;

  @Size(min = 1, message = "복용 요일은 최소 1개 이상 선택해야 합니다.")
  private List<String> takeDays;

  @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "MEDICATION_INVALID_TIME_FORMAT")
  private String takeTime;


  @Builder
  public MedicationUpdateRequestDto(
      String name,
      List<String> typeTags,
      List<String> takeDays,
      String takeTime) {
    this.name = name;
    this.typeTags = typeTags;
    this.takeDays = takeDays;
    this.takeTime = takeTime;
  }
}
