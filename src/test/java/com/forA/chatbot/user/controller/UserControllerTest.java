package com.forA.chatbot.user.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.diary.dto.DiaryReminderUpdateResponse;
import com.forA.chatbot.diary.service.DiaryReminderService;
import com.forA.chatbot.user.dto.NicknameResponse;
import com.forA.chatbot.user.dto.UserProfileResponse;
import com.forA.chatbot.user.service.UserService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private DiaryReminderService diaryReminderService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("createNickname returns common success response")
  void createNickname_success() throws Exception {
    authenticateAsUser(1L);
    given(userService.updateNickname(1L, "mori")).willReturn(new NicknameResponse("mori"));

    mockMvc
        .perform(
            post("/api/v1/users/nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"mori\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus._OK.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus._OK.getMessage()))
        .andExpect(jsonPath("$.result.nickname").value("mori"));

    verify(userService).updateNickname(1L, "mori");
  }

  @Test
  @DisplayName("createNickname returns validation error for blank nickname")
  void createNickname_validationFailure() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            post("/api/v1/users/nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus._BAD_REQUEST.getMessage()))
        .andExpect(jsonPath("$.result.nickname").value("닉네임은 필수입니다"));

    verify(userService, never()).updateNickname(anyLong(), anyString());
  }

  @Test
  @DisplayName("createNickname maps user business error through exception advice")
  void createNickname_userNotFound() throws Exception {
    authenticateAsUser(1L);
    given(userService.updateNickname(eq(1L), eq("mori")))
        .willThrow(new UserHandler(ErrorStatus.USER_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/v1/users/nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"mori\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus.USER_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus.USER_NOT_FOUND.getMessage()));

    verify(userService).updateNickname(1L, "mori");
  }

  @Test
  @DisplayName("getUserProfile returns timezone and diary reminders")
  void getUserProfile_success() throws Exception {
    authenticateAsUser(1L);
    UserProfileResponse response =
        new UserProfileResponse(
            1L,
            "mori",
            "unknown",
            2000,
            List.of("대학생"),
            List.of("우울증"),
            Map.of("우울증", List.of("무기력감")),
            "Asia/Seoul",
            List.of(new UserProfileResponse.DiaryReminder("21:00", true)),
            3);
    given(userService.getUserProfile(1L)).willReturn(response);

    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus._OK.getCode()))
        .andExpect(jsonPath("$.result.timeZone").value("Asia/Seoul"))
        .andExpect(jsonPath("$.result.diaryReminders[0].time").value("21:00"))
        .andExpect(jsonPath("$.result.diaryReminders[0].enabled").value(true))
        .andExpect(jsonPath("$.result.diaryReminderMaxCount").value(3));

    verify(userService).getUserProfile(1L);
  }

  @Test
  @DisplayName("updateDiaryReminders returns diary reminder success response")
  void updateDiaryReminders_success() throws Exception {
    authenticateAsUser(1L);
    DiaryReminderUpdateResponse response = new DiaryReminderUpdateResponse(
        java.util.List.of(new DiaryReminderUpdateResponse.Reminder("21:00", true)), 3);
    given(diaryReminderService.updateDiaryReminders(eq(1L), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/v1/users/me/diary-reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reminders\":[{\"time\":\"21:00\",\"enabled\":true}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value("DIARY_REMINDER2001"))
        .andExpect(jsonPath("$.result.reminders[0].time").value("21:00"))
        .andExpect(jsonPath("$.result.reminders[0].enabled").value(true))
        .andExpect(jsonPath("$.result.maxCount").value(3));

    verify(diaryReminderService).updateDiaryReminders(eq(1L), any());
  }

  private void authenticateAsUser(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(userId), null, Collections.emptyList()));
  }
}
