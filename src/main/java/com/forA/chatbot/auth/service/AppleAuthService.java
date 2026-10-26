package com.forA.chatbot.auth.service;

import com.forA.chatbot.auth.converter.AuthConverter;
import com.forA.chatbot.auth.domain.RefreshToken;
import com.forA.chatbot.auth.dto.AppleLoginRequest;
import com.forA.chatbot.auth.dto.AuthResponse;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.auth.jwt.JwtUtil;
import com.forA.chatbot.auth.repository.RefreshTokenRepository;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppleAuthService {

  private final AppleTokenValidator appleTokenValidator;
  private final UserRepository userRepository;
  private final JwtUtil jwtUtil;
  private final RefreshTokenRepository refreshTokenRepository;
  private final ChatSessionRepository chatSessionRepository;
  private final AuthConverter authConverter;

  @Transactional
  public AuthResponse authenticateWithApple(AppleLoginRequest request) {
    // 1. 애플 신원 토큰 검증
    Claims claims = appleTokenValidator.validateToken(request.getIdentityToken());

    // 2. 애플 사용자 정보 추출
    String appleUniqueId = claims.getSubject();
    String email = claims.get("email", String.class);

    // 3. 기존 사용자 조회 또는 신규 생성
    Optional<User> existingUser = userRepository.findByProviderTypeAndProviderUserId(ProviderType.APPLE, appleUniqueId);
    User user;
    boolean isNewUser = false;
    boolean isRestoredUser = false;
    if (existingUser.isPresent()) {
      log.info("기존 사용자 가입 : {}***", appleUniqueId.substring(0, Math.min(6, appleUniqueId.length())));
      user = existingUser.get();

      // 탈퇴한 회원이면 복구
      if (user.isDeleted()) {
        user.activate();
        isRestoredUser = true;
        log.info("탈퇴 회원 계정 복구 완료: userId={}", user.getId());
      }
    } else {
      log.info("신규 사용자 가입 : {}***", appleUniqueId.substring(0, Math.min(6, appleUniqueId.length())));
      isNewUser = true;

      user = User.builder()
          .providerUserId(appleUniqueId)
          .providerType(ProviderType.APPLE)
          .email(email)
          .fullName(buildFullName(request.getFirstName(), request.getLastName()))
          .firstName(request.getFirstName())
          .lastName(request.getLastName())
          .gender(Gender.UNKNOWN)
          .build();

      user = userRepository.save(user);
      log.info("신규 사용자 저장 완료 - userId: {}", user.getId());
    }
    // 4. JWT 토큰 생성
    String accessToken = jwtUtil.createAccessToken(String.valueOf(user.getId()));
    String refreshToken = createRefreshToken(user.getId());

    boolean onboardingCompleted = chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(user.getId())
        .map(ChatSession::getOnboardingCompleted)
        .orElse(isUserProfileCompleted(user));
    // 5. 응답 생성
    return authConverter.toAuthResponse(accessToken, refreshToken, 86400L, user, isNewUser,
        onboardingCompleted, isRestoredUser);
  }

  public String createRefreshToken(Long userId) {
    Optional<RefreshToken> refreshToken = refreshTokenRepository.findByUserId(userId);
    if (refreshToken.isPresent()) {
      refreshTokenRepository.deleteByUserId(userId);
    }
    String createRefreshToken = jwtUtil.createRefreshToken();
    // 갱신 토큰 생성
    refreshTokenRepository.save(
        RefreshToken.builder()
            .token(createRefreshToken)
            .userId(userId)
            .expiresAt(LocalDateTime.now().plusMonths(6))
            .build());
    return createRefreshToken;
  }

  private String buildFullName(String firstName, String lastName) {
    if (firstName != null && lastName != null) {
      return firstName + " " + lastName;
    } else if (firstName != null) {
      return firstName;
    } else if (lastName != null) {
      return lastName;
    }
    return "Apple User"; // 기본값
  }

  private boolean isUserProfileCompleted(User user) {
    return user.getGender() != null
        && user.getGender() != Gender.UNKNOWN
        && user.getBirthYear() != null
        && !user.getJobs().isEmpty()
        && !user.getDisorders().isEmpty()
        && (user.getDisorders().contains(DisorderType.NONE) || !user.getSymptoms().isEmpty());
  }
}
