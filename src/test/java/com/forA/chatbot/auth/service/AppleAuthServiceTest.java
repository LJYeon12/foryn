package com.forA.chatbot.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.forA.chatbot.auth.converter.AuthConverter;
import com.forA.chatbot.auth.domain.RefreshToken;
import com.forA.chatbot.auth.dto.AppleLoginRequest;
import com.forA.chatbot.auth.dto.AuthResponse;
import com.forA.chatbot.auth.jwt.JwtUtil;
import com.forA.chatbot.auth.repository.RefreshTokenRepository;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.user.domain.User;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppleAuthServiceTest {

    @InjectMocks
    private AppleAuthService appleAuthService;

    @Mock
    private AppleTokenValidator appleTokenValidator;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private AuthConverter authConverter;

    @Mock
    private Claims claims;

    private AppleLoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        loginRequest = new AppleLoginRequest();
        loginRequest.setIdentityToken("valid-identity-token");
        loginRequest.setFirstName("테스트");
        loginRequest.setLastName("유저");

        given(appleTokenValidator.validateToken(anyString())).willReturn(claims);
        given(claims.getSubject()).willReturn("apple-unique-id");
        given(claims.get("email", String.class)).willReturn("test@test.com");
        given(jwtUtil.createAccessToken(anyString())).willReturn("mock-access-token");
        given(jwtUtil.createRefreshToken()).willReturn("mock-refresh-token");
        given(refreshTokenRepository.findByUserId(any())).willReturn(Optional.empty());
        given(refreshTokenRepository.save(any(RefreshToken.class))).willReturn(null);
        // 접근 토큰, 갱신 토큰, 만료 시간, 사용자, 신규 여부, 온보딩 완료 여부, 복구 여부 순서
        given(authConverter.toAuthResponse(anyString(), anyString(), anyLong(), any(User.class),
                anyBoolean(), anyBoolean(), anyBoolean()))
            .willAnswer(inv -> AuthResponse.builder()
                .accessToken(inv.getArgument(0))
                .isNewUser(inv.getArgument(4))
                .isRestoredUser(inv.getArgument(6))
                .build());
    }

    @Test
    @DisplayName("탈퇴한 유저가 재로그인하면 isRestoredUser=true를 반환한다")
    void 탈퇴_유저_재로그인_시_isRestoredUser_true() {
        // 준비: 탈퇴 상태인 기존 사용자
        User deletedUser = User.builder()
                .email("test@test.com")
                .providerUserId("apple-unique-id")
                .providerType(ProviderType.APPLE)
                .fullName("테스트 유저")
                .gender(Gender.UNKNOWN)
                .build();
        deletedUser.deactivate(); // 논리 삭제 상태로 만든다.

        given(userRepository.findByProviderTypeAndProviderUserId(ProviderType.APPLE, "apple-unique-id"))
                .willReturn(Optional.of(deletedUser));
        given(chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(any()))
                .willReturn(Optional.empty());

        // 실행
        AuthResponse response = appleAuthService.authenticateWithApple(loginRequest);

        // 검증: 재가입 사용자이므로 isRestoredUser=true
        assertThat(response.isRestoredUser()).isTrue();
        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    @DisplayName("신규 유저가 가입하면 isRestoredUser=false를 반환한다")
    void 신규_유저_가입_시_isRestoredUser_false() {
        // 준비: 기존 사용자 없음
        given(userRepository.findByProviderTypeAndProviderUserId(ProviderType.APPLE, "apple-unique-id"))
                .willReturn(Optional.empty());

        User savedUser = User.builder()
                .email("test@test.com")
                .providerUserId("apple-unique-id")
                .providerType(ProviderType.APPLE)
                .fullName("테스트 유저")
                .gender(Gender.UNKNOWN)
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(any()))
                .willReturn(Optional.empty());

        // 실행
        AuthResponse response = appleAuthService.authenticateWithApple(loginRequest);

        // 검증
        assertThat(response.isRestoredUser()).isFalse();
        assertThat(response.isNewUser()).isTrue();
    }

    @Test
    @DisplayName("기존 유저(미탈퇴)가 로그인하면 isRestoredUser=false를 반환한다")
    void 기존_유저_로그인_시_isRestoredUser_false() {
        // 준비: 탈퇴하지 않은 기존 사용자
        User activeUser = User.builder()
                .email("test@test.com")
                .providerUserId("apple-unique-id")
                .providerType(ProviderType.APPLE)
                .fullName("테스트 유저")
                .gender(Gender.UNKNOWN)
                .build();

        given(userRepository.findByProviderTypeAndProviderUserId(ProviderType.APPLE, "apple-unique-id"))
                .willReturn(Optional.of(activeUser));
        given(chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(any()))
                .willReturn(Optional.empty());

        // 실행
        AuthResponse response = appleAuthService.authenticateWithApple(loginRequest);

        // 검증
        assertThat(response.isRestoredUser()).isFalse();
        assertThat(response.isNewUser()).isFalse();
    }
}
