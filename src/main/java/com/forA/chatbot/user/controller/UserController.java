package com.forA.chatbot.user.controller;

import com.forA.chatbot.apiPayload.ApiResponse;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.diary.dto.DiaryReminderUpdateRequest;
import com.forA.chatbot.diary.dto.DiaryReminderUpdateResponse;
import com.forA.chatbot.diary.service.DiaryReminderService;
import com.forA.chatbot.user.dto.NicknameRequest;
import com.forA.chatbot.user.dto.NicknameResponse;
import com.forA.chatbot.user.dto.UserDeleteResponse;
import com.forA.chatbot.user.dto.UserProfileResponse;
import com.forA.chatbot.user.dto.UserProfileUpdateRequest;
import com.forA.chatbot.user.dto.UserResetResponse;
import com.forA.chatbot.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;
  private final DiaryReminderService diaryReminderService;

  @PostMapping("/nickname")
  public ApiResponse<NicknameResponse> createNickname(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody NicknameRequest request) {
    Long userId = userDetails.getUserId();
    NicknameResponse response = userService.updateNickname(userId, request.getNickname());

    return ApiResponse.onSuccess(response);
  }

  @PatchMapping("/me")
  public ApiResponse<UserProfileResponse> updateUserProfile(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody UserProfileUpdateRequest request) {
    Long userId = userDetails.getUserId();
    UserProfileResponse response = userService.updateUserProfile(userId, request);

    return ApiResponse.onSuccess(response);
  }

  @GetMapping("/me")
  public ApiResponse<UserProfileResponse> getUserProfile(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails.getUserId();
    UserProfileResponse response = userService.getUserProfile(userId);

    return ApiResponse.onSuccess(response);
  }

  @PutMapping("/me/diary-reminders")
  public ApiResponse<DiaryReminderUpdateResponse> updateDiaryReminders(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody DiaryReminderUpdateRequest request) {
    Long userId = userDetails.getUserId();
    // 알림 설정은 전체 교체 방식이다. 실제 Cloud Tasks 재예약은 저장된 작업이 후속 처리한다.
    DiaryReminderUpdateResponse response =
        diaryReminderService.updateDiaryReminders(userId, request);

    return ApiResponse.of(SuccessStatus.DIARY_REMINDERS_UPDATED, response);
  }

  /**
   * 사용자 정보 초기화
   * */
  @PostMapping("/me/reset")
  public ApiResponse<UserResetResponse> resetUserData(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails.getUserId();
    UserResetResponse response = userService.resetUserData(userId);

    return ApiResponse.onSuccess(response);
  }

  @DeleteMapping("/me")
  public ApiResponse<UserDeleteResponse> deactivateUser(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails.getUserId();
    UserDeleteResponse response = userService.deactivateUser(userId);

    return ApiResponse.onSuccess(response);
  }
}
