package com.forA.chatbot.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

public class ReportResponseDto {

  /**
   * 주간 리포트 응답 DTO
   */
  @Getter
  @Builder
  public static class WeeklyReportResponse {
    private List<DailyMedicationReportDto> weekData;
  }

  /**
   * 월간 리포트 응답 DTO
   */
  @Getter
  @Builder
  public static class MonthlyReportResponse {
    private int year;       // 조회 년도
    private int month;      // 조회 월
    // 일관성을 위해 필드명을 monthData로 변경
    private List<DailyMedicationReportDto> monthData;
  }

  /**
   * 일별 상세 리포트 DTO (주간/월간 공통 사용)
   */
  @Getter
  @Builder
  public static class DailyMedicationReportDto {
    private String date; // "2025-08-18"
    private String dayOfWeek; // "월"
    private List<MedicationStatusDto> medications;
  }

  /**
   * 개별 복용 계획 상태 DTO (주간/월간 공통 사용)
   */
  @Getter
  @Builder
  public static class MedicationStatusDto {
    private Long bundleId;       // 어떤 복용 계획인지 식별자
    private String bundleName;   // 복용 계획 이름 (e.g., "아침 비타민")
    private String scheduledTime;    // 알람 시간 (e.g., "09:00:00")
    private boolean isTaken;     // 복용 여부
  }

  // ========== 감정 리포트 관련 DTO ==========

  /**
   * 주간 감정 리포트 응답 DTO
   */
  @Getter
  @Builder
  public static class WeeklyEmotionReportResponse {
    private List<DailyEmotionReportDto> currentWeek;
    private List<DailyEmotionReportDto> previousWeek;
  }

  @Getter
  @Builder
  public static class MonthlyEmotionReportResponse {
    private int year;       // 조회 년도
    private int month;      // 조회 월
    private MonthlyEmotionSummaryDto summary;
    private List<DailyEmotionReportDto> monthData;

  }
  @Getter
  @Builder
  public static class MonthlyEmotionSummaryDto {
    private Boolean hasRecentDiaryData;
    private String content;
  }

  /**
   * 일별 감정 리포트 DTO
   */
  @Getter
  @Builder
  public static class DailyEmotionReportDto {
    private String date; // YYYY-MM-DD 형식 (예: "2025-10-24")
    private String dayOfWeek; // 영어 대문자 (예: "MONDAY")
    private Double emotionScore; // 감정 평균 점수 (소수점 포함)
  }

  @Getter
  @Builder
  public static class WeeklyReportSummaryResponse {
    private Boolean hasEmotionData;
    private Boolean hasMedicationData;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double medicationComplianceRate;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double emotionImprovementRate;
  }


}

