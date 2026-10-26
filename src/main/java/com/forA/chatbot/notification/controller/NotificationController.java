package com.forA.chatbot.notification.controller;

import com.forA.chatbot.apiPayload.ApiResponse;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.notification.dto.TokenRefreshRequestDto;
import com.forA.chatbot.notification.dto.TokenRefreshResponseDto;
import com.forA.chatbot.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  @PostMapping("/token")
  public ApiResponse<TokenRefreshResponseDto> refreshToken(
      @Valid @RequestBody TokenRefreshRequestDto requestDto,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails.getUserId();

    notificationService.syncDeviceToken(userId, requestDto);
    TokenRefreshResponseDto responseDto =
        TokenRefreshResponseDto.builder().refreshedToken(requestDto.getToken()).build();

    return ApiResponse.of(SuccessStatus._TOKEN_REFRESH_SUCCESS, responseDto);
  }
}
