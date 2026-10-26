package com.forA.chatbot.report.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.report.dto.ReportResponseDto;
import com.forA.chatbot.user.domain.User;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportConverterTest {

  private final ReportConverter reportConverter = new ReportConverter();

  @Test
  @DisplayName("toMedicationStatus maps bundle and log to medication status DTO")
  void toMedicationStatus_mapsBundleAndLog() {
    MedicationBundle bundle =
        MedicationBundle.builder()
            .user(User.builder().id(1L).email("user@example.com").build())
            .bundleName("vitamin")
            .dayOfWeek("MONDAY")
            .scheduledTime(LocalTime.of(9, 0))
            .build();
    ReflectionTestUtils.setField(bundle, "id", 10L);
    MedicationLog log =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(LocalDate.of(2026, 3, 17))
            .isTaken(true)
            .build();

    ReportResponseDto.MedicationStatusDto result =
        reportConverter.toMedicationStatus(bundle, log);

    assertThat(result.getBundleId()).isEqualTo(10L);
    assertThat(result.getBundleName()).isEqualTo("vitamin");
    assertThat(result.getScheduledTime()).isEqualTo("09:00");
    assertThat(result.isTaken()).isTrue();
  }

  @Test
  @DisplayName("toDailyMedicationReport uses ISO date and Korean day-of-week label")
  void toDailyMedicationReport_mapsDateAndLabel() {
    ReportResponseDto.DailyMedicationReportDto result =
        reportConverter.toDailyMedicationReport(
            LocalDate.of(2026, 3, 17),
            DayOfWeek.TUESDAY,
            List.of());

    assertThat(result.getDate()).isEqualTo("2026-03-17");
    assertThat(result.getDayOfWeek()).isEqualTo("화");
    assertThat(result.getMedications()).isEmpty();
  }

  @Test
  @DisplayName("toMonthlyMedicationReport wraps year, month and day data")
  void toMonthlyMedicationReport_wrapsPayload() {
    List<ReportResponseDto.DailyMedicationReportDto> monthData =
        List.of(
            ReportResponseDto.DailyMedicationReportDto.builder()
                .date("2026-03-17")
                .dayOfWeek("화")
                .medications(List.of())
                .build());

    ReportResponseDto.MonthlyReportResponse result =
        reportConverter.toMonthlyMedicationReport(YearMonth.of(2026, 3), monthData);

    assertThat(result.getYear()).isEqualTo(2026);
    assertThat(result.getMonth()).isEqualTo(3);
    assertThat(result.getMonthData()).containsExactlyElementsOf(monthData);
  }

  @Test
  @DisplayName("toWeeklyReportSummary sets data flags from nullable rates")
  void toWeeklyReportSummary_setsFlags() {
    ReportResponseDto.WeeklyReportSummaryResponse result =
        reportConverter.toWeeklyReportSummary(null, 85.5);

    assertThat(result.getHasEmotionData()).isFalse();
    assertThat(result.getHasMedicationData()).isTrue();
    assertThat(result.getEmotionImprovementRate()).isNull();
    assertThat(result.getMedicationComplianceRate()).isEqualTo(85.5);
  }

  @Test
  @DisplayName("toDailyEmotionReport preserves null emotionScore")
  void toDailyEmotionReport_preservesNullEmotionScore() {
    ReportResponseDto.DailyEmotionReportDto result =
        reportConverter.toDailyEmotionReport(
            LocalDate.of(2026, 3, 17),
            "TUESDAY",
            null);

    assertThat(result.getDate()).isEqualTo("2026-03-17");
    assertThat(result.getDayOfWeek()).isEqualTo("TUESDAY");
    assertThat(result.getEmotionScore()).isNull();
  }

  @Test
  @DisplayName("toMonthlyEmotionReport wraps summary and month data")
  void toMonthlyEmotionReport_wrapsSummary() {
    ReportResponseDto.MonthlyEmotionSummaryDto summary =
        ReportResponseDto.MonthlyEmotionSummaryDto.builder()
            .hasRecentDiaryData(true)
            .content("요약")
            .build();
    List<ReportResponseDto.DailyEmotionReportDto> monthData =
        List.of(
            ReportResponseDto.DailyEmotionReportDto.builder()
                .date("2026-03-17")
                .dayOfWeek("TUESDAY")
                .emotionScore(null)
                .build());

    ReportResponseDto.MonthlyEmotionReportResponse result =
        reportConverter.toMonthlyEmotionReport(YearMonth.of(2026, 3), summary, monthData);

    assertThat(result.getYear()).isEqualTo(2026);
    assertThat(result.getMonth()).isEqualTo(3);
    assertThat(result.getSummary()).isEqualTo(summary);
    assertThat(result.getMonthData()).containsExactlyElementsOf(monthData);
  }
}
