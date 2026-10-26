package com.forA.chatbot.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.auth.jwt.JwtUtil;
import com.forA.chatbot.auth.repository.RefreshTokenRepository;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RemovedTempLoginSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RefreshTokenRepository refreshTokenRepository;

  @Autowired
  private JwtUtil jwtUtil;

  private User user;

  @BeforeEach
  void createAccount() {
    String identifier = UUID.randomUUID().toString();
    user = userRepository.saveAndFlush(User.builder()
        .email(identifier + "@example.com")
        .providerUserId(identifier)
        .providerType(ProviderType.APPLE)
        .fullName("Test User")
        .build());
  }

  @Test
  @DisplayName("이메일만 제출하는 임시 로그인은 인증 필터에서 거절된다")
  void tempLogin_withoutAuthentication_isForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/auth/temp-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + user.getEmail() + "\"}"))
        .andExpect(status().isForbidden());

    assertThat(refreshTokenRepository.findByUserId(user.getId())).isEmpty();
  }

  @Test
  @DisplayName("인증된 사용자에게도 임시 로그인 경로와 토큰 발급 기능은 없다")
  void tempLogin_withAuthentication_hasNoHandler() throws Exception {
    String accessToken = jwtUtil.createAccessToken(user.getId().toString());

    mockMvc.perform(post("/api/v1/auth/temp-login")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + user.getEmail() + "\"}"))
        .andExpect(status().isNotFound());

    assertThat(refreshTokenRepository.findByUserId(user.getId())).isEmpty();
  }

  @Test
  @DisplayName("정식 토큰 갱신은 인증 없이도 입력 검증까지 도달한다")
  void refresh_withoutAuthentication_stillValidatesInput() throws Exception {
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Apple 로그인은 인증 없이도 신원 증거 검증까지 도달한다")
  void appleLogin_withoutAuthentication_stillValidatesIdentity() throws Exception {
    mockMvc.perform(post("/api/v1/auth/apple")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("로그아웃은 인증 없이 호출할 수 없다")
  void logout_withoutAuthentication_isForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("명시적으로 허용하지 않은 인증 경로는 공개되지 않는다")
  void unknownAuthPath_withoutAuthentication_isForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/auth/unknown"))
        .andExpect(status().isForbidden());
  }
}
