package com.forA.chatbot.medications.controller;

import com.forA.chatbot.apiPayload.ApiResponse;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.medications.dto.MedicationLogRequestDto;
import com.forA.chatbot.medications.dto.MedicationLogResponseDto;
import com.forA.chatbot.medications.dto.MedicationRequestDto;
import com.forA.chatbot.medications.dto.MedicationResponseDto;
import com.forA.chatbot.medications.dto.MedicationUpdateRequestDto;
import com.forA.chatbot.medications.dto.TodayMedicationResponseDto;
import com.forA.chatbot.medications.service.MedicationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/medications")
@RequiredArgsConstructor
public class MedicationController {

  private final MedicationService medicationService;

  /** 약 복용 계획 생성 */
  @PostMapping("/plan")
  public ApiResponse<MedicationResponseDto> createMedicationPlan(
      @Valid @RequestBody MedicationRequestDto requestDto,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    log.info("userDetails: {}", userDetails);
    Long userId = userDetails.getUserId();
    log.info("약 복용 계획 생성 요청 - 사용자 ID: {}, 약 이름: {}", userId, requestDto.getName());

    MedicationResponseDto responseDto = medicationService.createMedicationPlan(userId, requestDto);

    log.info("약 복용 계획 생성 완료 - 사용자 ID: {}, 약 이름: {}", userId, responseDto.getName());

    return ApiResponse.of(SuccessStatus.MEDICATION_CREATED, responseDto);
  }

  /** 약 복용 계획 수정 */
  @PatchMapping("/plan/{planId}")
  public ApiResponse<MedicationResponseDto> updateMedicationPlan(
      @PathVariable Long planId,
      @Valid @RequestBody MedicationUpdateRequestDto requestDto,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getUserId();
    log.info("약 복용 계획 수정 요청 - 사용자 ID: {}, 계획 ID: {}", userId, planId);

    MedicationResponseDto responseDto =
        medicationService.updateMedicationPlan(userId, planId, requestDto);

    log.info("약 복용 계획 수정 완료 - 사용자 ID: {}, 계획 ID: {}", userId, planId);

    return ApiResponse.of(SuccessStatus.MEDICATION_UPDATED, responseDto);
  }

  /** 약 복용 계획 삭제 */
  @DeleteMapping("/plan/{planId}")
  public ApiResponse<Void> deleteMedicationPlan(
      @PathVariable Long planId, @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getUserId();
    log.info("약 복용 계획 삭제 요청 - 사용자 ID: {}, 계획 ID: {}", userId, planId);

    medicationService.deleteMedicationPlan(userId, planId);

    log.info("약 복용 계획 삭제 완료 - 사용자 ID: {}, 계획 ID: {}", userId, planId);

    return ApiResponse.of(SuccessStatus.MEDICATION_DELETED, null);
  }

  /** 약 복용 기록 생성 */
  @PostMapping("/log")
  public ApiResponse<MedicationLogResponseDto> createMedicationLog(
      @Valid @RequestBody MedicationLogRequestDto requestDto,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails.getUserId();
    log.info("약 복용 기록 요청 수신 - 사용자 ID: {}, medicationId: {}", userId, requestDto.getMedicationId());

    MedicationLogResponseDto responseDto = medicationService.createLog(userId, requestDto);

    log.info("약 복용 기록 생성 완료 - historyId: {}", responseDto.getHistoryId());

    return ApiResponse.of(SuccessStatus.MEDICATION_LOG_CREATED, responseDto);
  }

  /** 일별 복약 계획 조회 */
  @GetMapping
  public ApiResponse<List<TodayMedicationResponseDto>> getDailyMedications(
      @RequestParam(value = "date", required = false) // 날짜를 보내지 않으면 date: null 오늘 날짜로 처리
      @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getUserId();
    log.info("오늘의 복약 계획 조회 요청 - 사용자 ID: {}", userId);

    List<TodayMedicationResponseDto> medications = medicationService.getDailyMedications(userId, date);

    log.info("오늘의 복약 계획 조회 완료 - 사용자 ID: {}, 계획 수: {}", userId, medications.size());

    return ApiResponse.of(SuccessStatus.MEDICATION_LIST_RETRIEVED, medications);
  }
}
