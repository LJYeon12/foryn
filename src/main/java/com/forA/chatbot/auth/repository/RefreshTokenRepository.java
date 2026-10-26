package com.forA.chatbot.auth.repository;

import com.forA.chatbot.auth.domain.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByToken(String token);

  Optional<RefreshToken> findByUserId(Long userId);

  void deleteByUserId(Long userId);

  void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
