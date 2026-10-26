package com.forA.chatbot.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.apiPayload.exception.handler.AuthHandler;
import com.forA.chatbot.auth.dto.RefreshTokenRequest;
import com.forA.chatbot.auth.dto.RefreshTokenResponse;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.auth.jwt.JwtUtil;
import com.forA.chatbot.auth.service.AppleAuthService;
import com.forA.chatbot.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AppleAuthService appleAuthService;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private JwtUtil jwtUtil;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @Test
  @DisplayName("refresh returns common success response")
  void refresh_success() throws Exception {
    given(authService.refreshAccessToken(any(RefreshTokenRequest.class)))
        .willReturn(
            RefreshTokenResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("refresh-token")
                .hasNickname(true)
                .nickname("mori")
                .build());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus._OK.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus._OK.getMessage()))
        .andExpect(jsonPath("$.result.accessToken").value("new-access-token"))
        .andExpect(jsonPath("$.result.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.result.hasNickname").value(true))
        .andExpect(jsonPath("$.result.nickname").value("mori"));

    verify(authService).refreshAccessToken(any(RefreshTokenRequest.class));
  }

  @Test
  @DisplayName("refresh returns validation error for blank refreshToken")
  void refresh_validationFailure() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus._BAD_REQUEST.getMessage()))
        .andExpect(jsonPath("$.result.refreshToken").exists());

    verify(authService, never()).refreshAccessToken(any(RefreshTokenRequest.class));
  }

  @Test
  @DisplayName("refresh maps token refresh failure through exception advice")
  void refresh_tokenRefreshFailed() throws Exception {
    given(authService.refreshAccessToken(any(RefreshTokenRequest.class)))
        .willThrow(new AuthHandler(ErrorStatus.TOKEN_REFRESH_FAILED));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"expired-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus.TOKEN_REFRESH_FAILED.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus.TOKEN_REFRESH_FAILED.getMessage()));

    verify(authService).refreshAccessToken(any(RefreshTokenRequest.class));
  }
}
