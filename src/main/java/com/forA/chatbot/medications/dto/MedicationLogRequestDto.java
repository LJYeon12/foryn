package com.forA.chatbot.medications.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MedicationLogRequestDto {

  @NotNull(message = "medicationId는 필수입니다")
  private Long medicationId;

  @NotBlank(message = "날짜는 필수입니다 (yyyy-MM-dd)")
  @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "날짜 형식이 올바르지 않습니다 (yyyy-MM-dd)")
  private String date;

  @NotBlank(message = "복용 상태는 필수입니다")
  @Pattern(regexp = "TAKEN|SKIPPED", message = "복용 상태는 TAKEN 또는 SKIPPED만 가능합니다")
  private String status;

  @Min(value = -2, message = "conditionLevel은 -2 이상이어야 합니다")
  @Max(value = 2, message = "conditionLevel은 2 이하이어야 합니다")
  private Integer conditionLevel; // status=TAKEN일 때만 필수

  @Builder
  public MedicationLogRequestDto(
      Long medicationId, String date, String status, Integer conditionLevel) {
    this.medicationId = medicationId;
    this.date = date;
    this.status = status;
    this.conditionLevel = conditionLevel;
  }
}
