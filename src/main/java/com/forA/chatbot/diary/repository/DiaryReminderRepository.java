package com.forA.chatbot.diary.repository;

import com.forA.chatbot.diary.domain.DiaryReminder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiaryReminderRepository extends JpaRepository<DiaryReminder, Long> {

  List<DiaryReminder> findByUserIdOrderByReminderTimeAsc(Long userId);

  List<DiaryReminder> findByEnabledTrue();

  List<DiaryReminder> findByUserIdAndEnabledTrueOrderByReminderTimeAsc(Long userId);

  @Query("""
      SELECT reminder
      FROM DiaryReminder reminder
      JOIN FETCH reminder.user
      WHERE reminder.user.id = :userId
        AND reminder.enabled = true
      ORDER BY reminder.reminderTime ASC
      """)
  List<DiaryReminder> findEnabledByUserIdWithUserOrderByReminderTimeAsc(
      @Param("userId") Long userId);

  @Modifying
  @Query("DELETE FROM DiaryReminder reminder WHERE reminder.user.id = :userId")
  void deleteByUserId(@Param("userId") Long userId);
}
