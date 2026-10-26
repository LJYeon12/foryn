package com.forA.chatbot.report.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.report.dto.ReportResponseDto;
import com.forA.chatbot.report.service.ReportService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ReportService reportService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("getWeeklyEmotionReport returns common success response")
  void getWeeklyEmotionReport_success() throws Exception {
    authenticateAsUser(1L);
    given(reportService.getWeeklyEmotionReport(1L)).willReturn(
        ReportResponseDto.WeeklyEmotionReportResponse.builder()
            .currentWeek(
                List.of(
                    ReportResponseDto.DailyEmotionReportDto.builder()
                        .date("2025-10-24")
                        .dayOfWeek("FRIDAY")
                        .emotionScore(1.5)
                        .build()))
            .previousWeek(
                List.of(
                    ReportResponseDto.DailyEmotionReportDto.builder()
                        .date("2025-10-17")
                        .dayOfWeek("FRIDAY")
                        .emotionScore(0.5)
                        .build()))
            .build());

    mockMvc
        .perform(get("/api/v1/reports/weekly/emotion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus._WEEKLY_EMOTION_REPORT_RETRIEVED.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus._WEEKLY_EMOTION_REPORT_RETRIEVED.getMessage()))
        .andExpect(jsonPath("$.result.currentWeek[0].date").value("2025-10-24"))
        .andExpect(jsonPath("$.result.currentWeek[0].dayOfWeek").value("FRIDAY"))
        .andExpect(jsonPath("$.result.currentWeek[0].emotionScore").value(1.5))
        .andExpect(jsonPath("$.result.previousWeek[0].date").value("2025-10-17"))
        .andExpect(jsonPath("$.result.previousWeek[0].emotionScore").value(0.5));

    verify(reportService).getWeeklyEmotionReport(1L);
  }

  @Test
  @DisplayName("getWeeklyEmotionReport maps business error through exception advice")
  void getWeeklyEmotionReport_userNotFound() throws Exception {
    authenticateAsUser(1L);
    given(reportService.getWeeklyEmotionReport(1L))
        .willThrow(new UserHandler(ErrorStatus.USER_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/reports/weekly/emotion"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus.USER_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus.USER_NOT_FOUND.getMessage()));

    verify(reportService).getWeeklyEmotionReport(1L);
  }

  @Test
  @DisplayName("getMonthlyMedicationReport uses default monthOffset")
  void getMonthlyMedicationReport_usesDefaultMonthOffset() throws Exception {
    authenticateAsUser(1L);
    given(reportService.getMonthlyMedicationReport(1L, 0)).willReturn(
        ReportResponseDto.MonthlyReportResponse.builder()
            .year(2025)
            .month(10)
            .monthData(Collections.emptyList())
            .build());

    mockMvc
        .perform(get("/api/v1/reports/monthly/medication"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus._MONTHLY_MEDICATION_REPORT_RETRIEVED.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus._MONTHLY_MEDICATION_REPORT_RETRIEVED.getMessage()))
        .andExpect(jsonPath("$.result.year").value(2025))
        .andExpect(jsonPath("$.result.month").value(10))
        .andExpect(jsonPath("$.result.monthData").isArray());

    verify(reportService).getMonthlyMedicationReport(1L, 0);
  }

  @Test
  @DisplayName("getMonthlyEmotionReport preserves null emotionScore in response")
  void getMonthlyEmotionReport_preservesNullEmotionScore() throws Exception {
    authenticateAsUser(1L);
    given(reportService.getMonthlyEmotionReport(1L, 0)).willReturn(
        ReportResponseDto.MonthlyEmotionReportResponse.builder()
            .year(2025)
            .month(10)
            .summary(
                ReportResponseDto.MonthlyEmotionSummaryDto.builder()
                    .hasRecentDiaryData(false)
                    .content("요약")
                    .build())
            .monthData(
                List.of(
                    ReportResponseDto.DailyEmotionReportDto.builder()
                        .date("2025-10-01")
                        .dayOfWeek("WEDNESDAY")
                        .emotionScore(null)
                        .build()))
            .build());

    mockMvc
        .perform(get("/api/v1/reports/monthly/emotion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(
            jsonPath("$.code")
                .value(SuccessStatus._MONTHLY_EMOTION_REPORT_RETRIEVED.getCode()))
        .andExpect(
            jsonPath("$.message")
                .value(SuccessStatus._MONTHLY_EMOTION_REPORT_RETRIEVED.getMessage()))
        .andExpect(jsonPath("$.result.year").value(2025))
        .andExpect(jsonPath("$.result.month").value(10))
        .andExpect(jsonPath("$.result.summary.hasRecentDiaryData").value(false))
        .andExpect(jsonPath("$.result.summary.content").value("요약"))
        .andExpect(jsonPath("$.result.monthData[0].date").value("2025-10-01"))
        .andExpect(jsonPath("$.result.monthData[0].dayOfWeek").value("WEDNESDAY"))
        .andExpect(jsonPath("$.result.monthData[0].emotionScore").value(nullValue()));

    verify(reportService).getMonthlyEmotionReport(1L, 0);
  }

  private void authenticateAsUser(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(userId), null, Collections.emptyList()));
  }
}
