package com.forA.chatbot.chat.repository;

import com.forA.chatbot.chat.domain.ChatSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {
  Optional<ChatSession> findFirstByUserIdAndOnboardingCompletedFalseOrderByStartedAtDesc(Long userId);
  Optional<ChatSession> findFirstByUserIdOrderByStartedAtDesc(Long userId);
  Optional<ChatSession> findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(Long userId);


  List<ChatSession> findByUserIdAndStartedAtBetween(
      Long userId, LocalDateTime startDateTime, LocalDateTime endDateTime);
  /**
   * 특정 사용자가 특정 기간 동안 시작한 세션의 개수를 반환
   */
  long countByUserIdAndStartedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

  /**
   * 특정 사용자의 세션 중, 특정 시간 이후에 종료된 세션 목록을 반환
   */
  List<ChatSession> findByUserIdAndEndedAtAfter(Long userId, LocalDateTime cutoffTime);

  List<ChatSession> findByUserId(Long userId);

  long countByUserId(Long userId);

  void deleteByUserId(Long userId);
}
