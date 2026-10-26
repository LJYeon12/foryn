package com.forA.chatbot.report.converter;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.report.dto.ReportResponseDto;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ReportConverter {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  public ReportResponseDto.WeeklyReportResponse toWeeklyMedicationReport(
      List<ReportResponseDto.DailyMedicationReportDto> weekData) {
    return ReportResponseDto.WeeklyReportResponse.builder()
        .weekData(weekData)
        .build();
  }

  public ReportResponseDto.MonthlyReportResponse toMonthlyMedicationReport(
      YearMonth targetMonth, List<ReportResponseDto.DailyMedicationReportDto> monthData) {
    return ReportResponseDto.MonthlyReportResponse.builder()
        .year(targetMonth.getYear())
        .month(targetMonth.getMonthValue())
        .monthData(monthData)
        .build();
  }

  public ReportResponseDto.MedicationStatusDto toMedicationStatus(
      MedicationBundle bundle, MedicationLog log) {
    boolean isTaken = log != null && Boolean.TRUE.equals(log.getIsTaken());

    return ReportResponseDto.MedicationStatusDto.builder()
        .bundleId(bundle.getId())
        .bundleName(bundle.getBundleName())
        .scheduledTime(bundle.getScheduledTime().format(TIME_FORMATTER))
        .isTaken(isTaken)
        .build();
  }

  public ReportResponseDto.DailyMedicationReportDto toDailyMedicationReport(
      LocalDate date,
      DayOfWeek dayOfWeek,
      List<ReportResponseDto.MedicationStatusDto> medications) {
    return ReportResponseDto.DailyMedicationReportDto.builder()
        .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .dayOfWeek(dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN))
        .medications(medications)
        .build();
  }

  public ReportResponseDto.WeeklyEmotionReportResponse toWeeklyEmotionReport(
      List<ReportResponseDto.DailyEmotionReportDto> currentWeek,
      List<ReportResponseDto.DailyEmotionReportDto> previousWeek) {
    return ReportResponseDto.WeeklyEmotionReportResponse.builder()
        .currentWeek(currentWeek)
        .previousWeek(previousWeek)
        .build();
  }

  public ReportResponseDto.MonthlyEmotionReportResponse toMonthlyEmotionReport(
      YearMonth targetMonth,
      ReportResponseDto.MonthlyEmotionSummaryDto summary,
      List<ReportResponseDto.DailyEmotionReportDto> monthData) {
    return ReportResponseDto.MonthlyEmotionReportResponse.builder()
        .year(targetMonth.getYear())
        .month(targetMonth.getMonthValue())
        .summary(summary)
        .monthData(monthData)
        .build();
  }

  public ReportResponseDto.DailyEmotionReportDto toDailyEmotionReport(
      LocalDate date, String dayOfWeek, Double emotionScore) {
    return ReportResponseDto.DailyEmotionReportDto.builder()
        .date(date.toString())
        .dayOfWeek(dayOfWeek)
        .emotionScore(emotionScore)
        .build();
  }

  public ReportResponseDto.WeeklyReportSummaryResponse toWeeklyReportSummary(
      Double emotionRate, Double medicationRate) {
    return ReportResponseDto.WeeklyReportSummaryResponse.builder()
        .hasEmotionData(emotionRate != null)
        .hasMedicationData(medicationRate != null)
        .emotionImprovementRate(emotionRate)
        .medicationComplianceRate(medicationRate)
        .build();
  }
}
