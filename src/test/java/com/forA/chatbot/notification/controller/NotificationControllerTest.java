package com.forA.chatbot.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.notification.dto.TokenRefreshRequestDto;
import com.forA.chatbot.notification.service.NotificationService;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private NotificationService notificationService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("refreshToken returns token refresh success response")
  void refreshToken_success() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            post("/api/v1/notifications/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"device-token\",\"isPushEnabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus._TOKEN_REFRESH_SUCCESS.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus._TOKEN_REFRESH_SUCCESS.getMessage()))
        .andExpect(jsonPath("$.result.refreshedToken").value("device-token"));

    verify(notificationService).syncDeviceToken(anyLong(), any(TokenRefreshRequestDto.class));
  }

  @Test
  @DisplayName("refreshToken returns validation error for blank token")
  void refreshToken_validationFailure() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            post("/api/v1/notifications/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\",\"isPushEnabled\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus._BAD_REQUEST.getMessage()))
        .andExpect(jsonPath("$.result.token").value("토큰은 필수입니다."));

    verify(notificationService, never()).syncDeviceToken(anyLong(), any(TokenRefreshRequestDto.class));
  }

  private void authenticateAsUser(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(userId), null, Collections.emptyList()));
  }
}
