package com.forA.chatbot.auth.repository;

import com.forA.chatbot.auth.domain.BlacklistedToken;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

  boolean existsByToken(String token);

  @Modifying
  @Transactional
  @Query("DELETE FROM BlacklistedToken bt WHERE bt.expiresAt < :now")
  void deleteExpiredTokens(@Param("now") LocalDateTime now);

  @Modifying
  @Transactional
  void deleteByUserId(Long userId);
}
