package com.forA.chatbot.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.auth.filter.OidcTokenFilter;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.config.InternalSecurityConfig;
import com.forA.chatbot.notification.service.DiaryReminderNotificationService;
import com.forA.chatbot.notification.service.MedicationOutboxDispatcher;
import com.forA.chatbot.notification.service.MedicationSchedulerService;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** 실제 보안 체인과 Google 검증기를 사용한다. 검증 키만 로컬 테스트 키 쌍에서 가져온다. */
@WebMvcTest(InternalNotificationController.class)
@AutoConfigureMockMvc
@Import({InternalSecurityConfig.class, OidcTokenFilter.class})
@TestPropertySource(properties = {
    "cloud-tasks.target-base-url=https://test.example.com",
    "cloud-tasks.task-execution-sa-email=local-runner@example.invalid"
})
@ExtendWith(OutputCaptureExtension.class)
class LocalOidcSecurityTest {
  private static final String AUDIENCE = "https://test.example.com";
  private static final String ISSUER = "https://accounts.google.com";
  private static final String ACCOUNT = "local-runner@example.invalid";
  private static final String RECOVER = "/internal/notifications/medication-outbox/recover";
  private static KeyPair trustedKey;
  private static KeyPair otherKey;

  @Autowired private MockMvc mvc;
  @MockitoBean private MedicationSchedulerService medicationSchedulerService;
  @MockitoBean private MedicationOutboxDispatcher dispatcher;
  @MockitoBean private DiaryReminderNotificationService diaryReminderNotificationService;
  @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

  @BeforeAll
  static void generateEphemeralKeys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    trustedKey = generator.generateKeyPair();
    otherKey = generator.generateKeyPair();
  }

  @BeforeEach
  void passThroughUnrelatedUserJwtFilter() throws Exception {
    // 서블릿 테스트 문맥은 사용자 JWT 필터도 등록한다. 해당 테스트 대역은 필터 체인을 다음으로 전달해야 한다.
    doAnswer(invocation -> {
      FilterChain chain = invocation.getArgument(2);
      chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
  }

  @Test
  @DisplayName("로컬 서명 토큰은 실제 보안 체인을 통과해 Outbox 복구와 재시도를 실행한다")
  void validTokenReachesProtectedServices() throws Exception {
    doAnswer(invocation -> {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      assertThat(authentication).isNotNull();
      assertThat(authentication.isAuthenticated()).isTrue();
      assertThat(authentication.getName()).isEqualTo(ACCOUNT);
      return null;
    }).when(dispatcher).recoverAndDispatch();
    try (var verifier = useLocalKey()) {
      String token = token("valid");
      mvc.perform(post(RECOVER).header("Authorization", "Bearer " + token))
          .andExpect(status().isOk());
      mvc.perform(post("/internal/notifications/medication-outbox/42/retry")
          .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
      verify(dispatcher).recoverAndDispatch();
      verify(dispatcher).retryFailed(42L);
    }
  }

  @ParameterizedTest(name = "유효하지 않은 토큰 거절: {0}")
  @ValueSource(strings = {"audience", "issuer", "expired", "signature", "malformed", "empty", "algorithm"})
  void invalidTokenNeverReachesService(String fault) throws Exception {
    try (var verifier = useLocalKey()) {
      mvc.perform(post(RECOVER).header("Authorization", "Bearer " + token(fault)))
          .andExpect(status().isUnauthorized());
      verifyNoInteractions(dispatcher, medicationSchedulerService, diaryReminderNotificationService);
    }
  }

  @ParameterizedTest(name = "실행 계정 불일치 거절: {0}")
  @ValueSource(strings = {"account", "missing-account", "non-string-account"})
  void wrongAccountIsForbidden(String fault) throws Exception {
    try (var verifier = useLocalKey()) {
      mvc.perform(post(RECOVER).header("Authorization", "Bearer " + token(fault)))
          .andExpect(status().isForbidden());
      verifyNoInteractions(dispatcher, medicationSchedulerService, diaryReminderNotificationService);
    }
  }

  @Test
  @DisplayName("거절된 토큰의 이메일과 알고리즘 원문을 로그에 남기지 않는다")
  void rejectedClaimsAreNotLogged(CapturedOutput output) throws Exception {
    try (var verifier = useLocalKey()) {
      mvc.perform(post(RECOVER).header("Authorization", "Bearer " + token("account")))
          .andExpect(status().isForbidden());
      mvc.perform(post(RECOVER).header("Authorization", "Bearer " + token("algorithm")))
          .andExpect(status().isUnauthorized());
      assertThat(output.getOut() + output.getErr()).doesNotContain(
          ACCOUNT, "other-account@example.invalid", "untrusted-algorithm-marker");
    }
  }

  @Test
  @DisplayName("Bearer 토큰 형식이 아니면 검증기를 만들거나 서비스를 호출하지 않는다")
  void missingOrWrongAuthorizationScheme() throws Exception {
    try (var verifier = useLocalKey()) {
      mvc.perform(post(RECOVER)).andExpect(status().isUnauthorized());
      mvc.perform(post(RECOVER).header("Authorization", "Basic synthetic"))
          .andExpect(status().isUnauthorized());
      verifier.verifyNoInteractions();
      verifyNoInteractions(dispatcher);
    }
  }

  @Test
  @DisplayName("검증 공개키를 얻지 못하면 내부 작업을 실행하지 않고 401을 반환한다")
  void unavailableVerificationKeyFailsClosed() throws Exception {
    TokenVerifier.Builder builder = TokenVerifier.newBuilder().setHttpTransportFactory(() -> {
      throw new IllegalStateException("synthetic key lookup failure");
    });
    try (var factory = mockStatic(TokenVerifier.class)) {
      factory.when(TokenVerifier::newBuilder).thenReturn(builder);
      mvc.perform(post(RECOVER).header("Authorization", "Bearer " + token("valid")))
          .andExpect(status().isUnauthorized());
      verifyNoInteractions(dispatcher, medicationSchedulerService, diaryReminderNotificationService);
    }
  }

  @Test
  @DisplayName("인증 뒤 서비스에서 발생한 입력 오류를 토큰 오류로 바꾸지 않는다")
  void downstreamFailureIsNotClassifiedAsTokenFailure() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", RECOVER);
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader("Authorization", "Bearer " + token("valid"));
    IllegalArgumentException failure = new IllegalArgumentException("synthetic downstream failure");
    try (var verifier = useLocalKey()) {
      assertThatThrownBy(() -> new OidcTokenFilter(AUDIENCE, ACCOUNT).doFilter(request, response,
          (incoming, outgoing) -> { throw failure; })).isSameAs(failure);
      assertThat(response.getStatus()).isEqualTo(200);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static MockedStatic<TokenVerifier> useLocalKey() {
    // 운영 필터의 실제 대상·발급자 설정을 빌더에서 그대로 유지한다.
    TokenVerifier.Builder builder = TokenVerifier.newBuilder().setPublicKey(trustedKey.getPublic())
        .setHttpTransportFactory(() -> { throw new AssertionError("External key requests are forbidden"); });
    MockedStatic<TokenVerifier> scoped = mockStatic(TokenVerifier.class);
    scoped.when(TokenVerifier::newBuilder).thenReturn(builder);
    return scoped;
  }

  private static String token(String fault) throws Exception {
    if ("empty".equals(fault)) { return ""; }
    if ("malformed".equals(fault)) { return "not-a-jwt"; }
    JsonWebToken.Payload payload = new JsonWebToken.Payload()
        .setAudience("audience".equals(fault) ? "https://wrong.example.invalid" : AUDIENCE)
        .setIssuer("issuer".equals(fault) ? "https://wrong-issuer.example.invalid" : ISSUER)
        .setIssuedAtTimeSeconds(Instant.now().minusSeconds(60).getEpochSecond())
        .setExpirationTimeSeconds(Instant.now().plusSeconds("expired".equals(fault) ? -10 : 600).getEpochSecond());
    if (!"missing-account".equals(fault)) {
      payload.set("email", "account".equals(fault) ? "other-account@example.invalid" : ACCOUNT);
    }
    if ("non-string-account".equals(fault)) { payload.set("email", 42); }
    JsonWebSignature.Header header = new JsonWebSignature.Header()
        .setAlgorithm("algorithm".equals(fault) ? "untrusted-algorithm-marker" : "RS256").setType("JWT");
    KeyPair signer = "signature".equals(fault) ? otherKey : trustedKey;
    return JsonWebSignature.signUsingRsaSha256((RSAPrivateKey) signer.getPrivate(),
        GsonFactory.getDefaultInstance(), header, payload);
  }
}
