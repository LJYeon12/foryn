package com.forA.chatbot.diary.repository;

import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

  List<Diary> findByUserIdAndDiaryDateOrderByCreatedAtAsc(Long userId, LocalDate diaryDate);

  @Query("""
      SELECT d
      FROM Diary d
      WHERE d.user.id = :userId
        AND d.diaryDate = :diaryDate
        AND d.status = com.forA.chatbot.diary.domain.enums.DiaryStatus.COMPLETED
      ORDER BY d.completedAt ASC, d.id ASC
      """)
  List<Diary> findCompletedByUserIdAndDiaryDateOrderByCompletedAtAsc(
      @Param("userId") Long userId, @Param("diaryDate") LocalDate diaryDate);

  @Query("""
      SELECT d
      FROM Diary d
      WHERE d.user.id = :userId
        AND d.diaryDate BETWEEN :startDate AND :endDate
        AND d.status = com.forA.chatbot.diary.domain.enums.DiaryStatus.COMPLETED
        AND d.emotionScore IS NOT NULL
      """)
  List<Diary> findCompletedEmotionScoresByUserIdAndDiaryDateBetween(
      @Param("userId") Long userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query("""
      SELECT d
      FROM Diary d
      WHERE d.user.id = :userId
        AND d.diaryDate BETWEEN :startDate AND :endDate
        AND d.status = com.forA.chatbot.diary.domain.enums.DiaryStatus.COMPLETED
      ORDER BY d.diaryDate ASC, d.completedAt ASC, d.id ASC
      """)
  List<Diary> findCompletedByUserIdAndDiaryDateBetweenOrderByDiaryDateAscCompletedAtAsc(
      @Param("userId") Long userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  Optional<Diary> findByIdAndUserId(Long id, Long userId);

  // 같은 일기를 두 번 완료하지 않도록 잠근다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT d
      FROM Diary d
      WHERE d.id = :id
        AND d.user.id = :userId
      """)
  Optional<Diary> findByIdAndUserIdForUpdate(
      @Param("id") Long id, @Param("userId") Long userId);

  @Query("""
      SELECT d
      FROM Diary d
      WHERE d.user.id = :userId
        AND d.diaryDate = :diaryDate
        AND d.status = com.forA.chatbot.diary.domain.enums.DiaryStatus.DRAFT
      """)
  Optional<Diary> findDraftByUserIdAndDiaryDate(
      @Param("userId") Long userId, @Param("diaryDate") LocalDate diaryDate);

  // 두 요청이 동시에 와도 같은 초안을 차례대로 수정한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT d
      FROM Diary d
      WHERE d.user.id = :userId
        AND d.diaryDate = :diaryDate
        AND d.status = com.forA.chatbot.diary.domain.enums.DiaryStatus.DRAFT
      """)
  Optional<Diary> findDraftByUserIdAndDiaryDateForUpdate(
      @Param("userId") Long userId,
      @Param("diaryDate") LocalDate diaryDate);

  // 이 사용자의 오래된 초안만 지운다. 완료한 일기와 다른 사람의 일기는 남긴다.
  @Modifying(flushAutomatically = true)
  @Query("""
      DELETE FROM Diary d
      WHERE d.user.id = :userId
        AND d.status = com.forA.chatbot.diary.domain.enums.DiaryStatus.DRAFT
        AND d.updatedAt < :cutoff
      """)
  int deleteExpiredDraftsByUpdatedAtBefore(
      @Param("userId") Long userId,
      @Param("cutoff") LocalDateTime cutoff);

  long countByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);

  long countByUserIdAndDiaryDateAndStatus(Long userId, LocalDate diaryDate, DiaryStatus status);
}
