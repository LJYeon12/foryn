package com.forA.chatbot.auth.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.AuthHandler;
import com.forA.chatbot.auth.converter.AuthConverter;
import com.forA.chatbot.auth.domain.RefreshToken;
import com.forA.chatbot.auth.dto.RefreshTokenRequest;
import com.forA.chatbot.auth.dto.RefreshTokenResponse;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.auth.jwt.JwtUtil;
import com.forA.chatbot.auth.repository.RefreshTokenRepository;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
  private final UserRepository userRepository;
  private final JwtUtil jwtUtil;
  private final RefreshTokenRepository refreshTokenRepository;
  private final BlacklistService blacklistService;
  private final DeviceTokenRepository deviceTokenRepository;
  private final AuthConverter authConverter;

  @Transactional
  public RefreshTokenResponse refreshAccessToken(RefreshTokenRequest request) {
    String refreshTokenValue = request.getRefreshToken();

    // 1. 갱신 토큰 유효성 검증
    RefreshToken refreshToken = refreshTokenRepository
        .findByToken(refreshTokenValue)
        .orElseThrow(() -> new AuthHandler(ErrorStatus.TOKEN_REFRESH_FAILED));

    // 2. 만료 확인
    if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
      refreshTokenRepository.delete(refreshToken);
      throw new AuthHandler(ErrorStatus.EXPIRED_REFRESH_TOKEN);
    }

    // 3. 사용자 정보 조회
    User user = userRepository
        .findById(refreshToken.getUserId())
        .orElseThrow(() -> new AuthHandler(ErrorStatus.USER_NOT_FOUND));

    // 4. 새 접근 토큰 생성
    String newAccessToken = jwtUtil.createAccessToken(String.valueOf(refreshToken.getUserId()));

    log.info("Access Token 갱신 완료: userId={}", refreshToken.getUserId());

    return authConverter.toRefreshTokenResponse(newAccessToken, refreshTokenValue, user);
  }

  @Transactional
  public void logout(Long userId, String accessToken) {
    // 1. 접근 토큰을 블랙리스트에 추가
    LocalDateTime tokenExpiration = getTokenExpiration(accessToken);
    blacklistService.addToBlacklist(accessToken, tokenExpiration, userId);

    // 2. 갱신 토큰 삭제
    refreshTokenRepository.findByUserId(userId).ifPresent(refreshTokenRepository::delete);

    // 3. 사용자의 기기 토큰(FCM) 모두 삭제 (로그아웃 시 푸시 알림 비활성화 하드 삭제)
    deviceTokenRepository.deleteAllByUserId(userId);

    log.info("로그아웃 처리 완료 (기기 토큰 삭제 포함): userId={}", userId);
  }

  private LocalDateTime getTokenExpiration(String accessToken) {
    try {
      // JWT에서 만료시간 추출
      return jwtUtil.getExpirationFromToken(accessToken);
    } catch (Exception e) {
      // 만료시간을 추출할 수 없는 경우, 현재시간 + 24시간으로 설정
      log.warn("토큰 만료시간 추출 실패, 기본값 사용: {}", e.getMessage());
      return LocalDateTime.now().plusHours(24);
    }
  }
}
