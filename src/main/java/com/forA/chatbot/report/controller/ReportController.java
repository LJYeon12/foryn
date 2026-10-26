package com.forA.chatbot.report.controller;

import com.forA.chatbot.apiPayload.ApiResponse;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.report.dto.ReportResponseDto;
import com.forA.chatbot.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
public class ReportController {

  private final ReportService reportService;

  @GetMapping("/weekly/medication")
  public ApiResponse<ReportResponseDto.WeeklyReportResponse> getWeeklyMedicationReport(
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getUserId();

    ReportResponseDto.WeeklyReportResponse response = reportService.getWeeklyMedicationReport(userId);

    return ApiResponse.of(SuccessStatus._WEEKLY_MEDICATION_REPORT_RETRIEVED,response);
  }

  @GetMapping("/monthly/medication")
  public ApiResponse<ReportResponseDto.MonthlyReportResponse> getMonthlyMedicationReport(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(value = "monthOffset", defaultValue = "0") int monthOffset) {

    Long userId = userDetails.getUserId();
    ReportResponseDto.MonthlyReportResponse response = reportService.getMonthlyMedicationReport(userId, monthOffset);

    return ApiResponse.of(SuccessStatus._MONTHLY_MEDICATION_REPORT_RETRIEVED,response);
  }

  /**
   * 주간 감정 리포트 조회
   * - currentWeek: 오늘 포함 이전 7일
   * - previousWeek: currentWeek 이전 7일
   */
  @GetMapping("/weekly/emotion")
  public ApiResponse<ReportResponseDto.WeeklyEmotionReportResponse> getWeeklyEmotionReport(
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getUserId();
    ReportResponseDto.WeeklyEmotionReportResponse response =
        reportService.getWeeklyEmotionReport(userId);

    return ApiResponse.of(SuccessStatus._WEEKLY_EMOTION_REPORT_RETRIEVED, response);
  }

  @GetMapping("/monthly/emotion")
  public ApiResponse<ReportResponseDto.MonthlyEmotionReportResponse> getMonthlyEmotionReport (
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(value = "monthOffset", defaultValue = "0") int monthOffset) {

    Long userId = userDetails.getUserId();
    ReportResponseDto.MonthlyEmotionReportResponse response = reportService.getMonthlyEmotionReport(userId, monthOffset);


    return ApiResponse.of(SuccessStatus._MONTHLY_EMOTION_REPORT_RETRIEVED, response);
  }

  @GetMapping("/weekly/summary")
  public ApiResponse<ReportResponseDto.WeeklyReportSummaryResponse> getWeeklyReportSummary(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails.getUserId();
    ReportResponseDto.WeeklyReportSummaryResponse response = reportService.getWeeklyReportSummary(userId);

    return ApiResponse.of(SuccessStatus._WEEKLY_REPORT_SUMMARY_RETRIEVED, response);

  }
}
