package com.forA.chatbot.auth.controller;

import com.forA.chatbot.apiPayload.ApiResponse;
import com.forA.chatbot.auth.dto.AppleLoginRequest;
import com.forA.chatbot.auth.dto.AuthResponse;
import com.forA.chatbot.auth.dto.LogoutResponse;
import com.forA.chatbot.auth.dto.RefreshTokenRequest;
import com.forA.chatbot.auth.dto.RefreshTokenResponse;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.auth.jwt.JwtUtil;
import com.forA.chatbot.auth.service.AppleAuthService;
import com.forA.chatbot.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

  private final AppleAuthService appleAuthService;
  private final AuthService authService;
  private final JwtUtil jwtUtil;

  @PostMapping("/apple")
  public ApiResponse<AuthResponse> appleLogin(@RequestBody AppleLoginRequest request) {
    log.info("Apple 로그인 요청 수신");
    AuthResponse response = appleAuthService.authenticateWithApple(request);
    log.info("Apple 로그인 성공: userId={}, isNewUser={}", response.getUserId(), response.isNewUser());
    return ApiResponse.onSuccess(response);
  }

  @PostMapping("/refresh")
  public ApiResponse<RefreshTokenResponse> refresh(
      @Valid @RequestBody RefreshTokenRequest request) {
    log.info("토큰 재발급 요청 수신");
    RefreshTokenResponse response = authService.refreshAccessToken(request);
    log.info("토큰 재발급 성공");
    return ApiResponse.onSuccess(response);
  }

  @PostMapping("/logout")
  public ApiResponse<LogoutResponse> logout(
      @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest request) {
    log.info("로그아웃 요청 수신: userId={}", userDetails.getUserId());

    // 현재 사용 중인 접근 토큰 추출
    String accessToken = jwtUtil.extractTokenFromRequest(request);

    authService.logout(userDetails.getUserId(), accessToken);
    return ApiResponse.onSuccess(new LogoutResponse("Successfully logged out"));
  }
}
