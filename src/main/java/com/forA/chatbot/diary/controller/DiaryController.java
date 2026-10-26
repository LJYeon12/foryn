package com.forA.chatbot.diary.controller;

import com.forA.chatbot.apiPayload.ApiResponse;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.diary.dto.DiaryCompleteRequest;
import com.forA.chatbot.diary.dto.DiaryCompleteResponse;
import com.forA.chatbot.diary.dto.DiaryCreateRequest;
import com.forA.chatbot.diary.dto.DiaryCreateResponse;
import com.forA.chatbot.diary.dto.DiaryDailyResponse;
import com.forA.chatbot.diary.service.DiaryService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/diaries")
public class DiaryController {

  private final DiaryService diaryService;

  @PostMapping
  public ApiResponse<DiaryCreateResponse> createDiaryDraft(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestBody DiaryCreateRequest request) {
    Long userId = userDetails.getUserId();
    log.info("한줄일기 작성 시작 요청 - 사용자 ID: {}, 작성 날짜: {}", userId, date);

    DiaryCreateResponse response = diaryService.createDiaryDraft(userId, date, request);

    log.info("한줄일기 작성 시작 완료 - 사용자 ID: {}, diaryId: {}", userId, response.diaryId());
    return ApiResponse.of(SuccessStatus.DIARY_CREATED, response);
  }

  @PostMapping("/{diaryId}/complete")
  public ApiResponse<DiaryCompleteResponse> completeDiary(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long diaryId,
      @RequestBody DiaryCompleteRequest request) {
    Long userId = userDetails.getUserId();
    log.info("한줄일기 작성 완료 요청 - 사용자 ID: {}, diaryId: {}", userId, diaryId);

    DiaryCompleteResponse response = diaryService.completeDiary(userId, diaryId, request);

    log.info("한줄일기 작성 완료 응답 - 사용자 ID: {}, diaryId: {}", userId, diaryId);
    return ApiResponse.of(SuccessStatus.DIARY_COMPLETED, response);
  }

  @GetMapping("/daily")
  public ApiResponse<DiaryDailyResponse> getDailyDiaries(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    Long userId = userDetails.getUserId();
    log.info("일별 한줄일기 조회 요청 - 사용자 ID: {}, 조회 날짜: {}", userId, date);

    DiaryDailyResponse response = diaryService.getDailyDiaries(userId, date);

    log.info("일별 한줄일기 조회 완료 - 사용자 ID: {}, 조회 날짜: {}", userId, date);
    return ApiResponse.of(SuccessStatus.DIARY_DAILY_RETRIEVED, response);
  }
}
