package com.forA.chatbot.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.dto.ChatResponse;
import com.forA.chatbot.chat.service.ChatService;
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

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ChatService chatService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("handleUserResponse returns common success response")
  void handleUserResponse_success() throws Exception {
    authenticateAsUser(1L);
    given(chatService.handleUserResponse(eq(1L), eq("session-1"), any(ChatRequest.class)))
        .willReturn(
            ChatResponse.builder()
                .sessionId("session-1")
                .currentStep("EMOTION_SELECT")
                .botMessage(
                    ChatBotMessage.builder()
                        .content("Choose the next emotion.")
                        .type(MessageType.OPTION)
                        .options(Collections.emptyList())
                        .build())
                .isCompleted(false)
                .onboardingCompleted(true)
                .todayChatCount(1)
                .build());

    mockMvc
        .perform(
            post("/api/v1/chat/session/session-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseValue\":\"happy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus._OK.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus._OK.getMessage()))
        .andExpect(jsonPath("$.result.sessionId").value("session-1"))
        .andExpect(jsonPath("$.result.currentStep").value("EMOTION_SELECT"))
        .andExpect(jsonPath("$.result.botMessage.content").value("Choose the next emotion."))
        .andExpect(jsonPath("$.result.isCompleted").value(false))
        .andExpect(jsonPath("$.result.todayChatCount").value(1));

    verify(chatService).handleUserResponse(eq(1L), eq("session-1"), any(ChatRequest.class));
  }

  @Test
  @DisplayName("handleUserResponse returns validation error for blank responseValue")
  void handleUserResponse_validationFailure() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            post("/api/v1/chat/session/session-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseValue\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus._BAD_REQUEST.getMessage()))
        .andExpect(jsonPath("$.result.responseValue").exists());

    verify(chatService, never()).handleUserResponse(eq(1L), eq("session-1"), any(ChatRequest.class));
  }

  @Test
  @DisplayName("handleUserResponse maps chat business error through exception advice")
  void handleUserResponse_sessionNotFound() throws Exception {
    authenticateAsUser(1L);
    given(chatService.handleUserResponse(eq(1L), eq("missing-session"), any(ChatRequest.class)))
        .willThrow(new ChatHandler(ErrorStatus.SESSION_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/v1/chat/session/missing-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseValue\":\"happy\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus.SESSION_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus.SESSION_NOT_FOUND.getMessage()));

    verify(chatService).handleUserResponse(eq(1L), eq("missing-session"), any(ChatRequest.class));
  }

  private void authenticateAsUser(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(userId), null, Collections.emptyList()));
  }
}




