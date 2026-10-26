package com.forA.chatbot.diary.repository;

import com.forA.chatbot.diary.domain.DiaryReminderRescheduleJob;
import com.forA.chatbot.diary.domain.enums.DiaryReminderRescheduleJobStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiaryReminderRescheduleJobRepository
    extends JpaRepository<DiaryReminderRescheduleJob, Long> {

  @Query("""
      SELECT job
      FROM DiaryReminderRescheduleJob job
      WHERE job.status = :status
        AND (job.nextRetryAt IS NULL OR job.nextRetryAt <= :now)
      ORDER BY job.createdAt ASC
      """)
  List<DiaryReminderRescheduleJob> findRunnableJobsByStatus(
      @Param("status") DiaryReminderRescheduleJobStatus status, @Param("now") LocalDateTime now);

  @Query("""
      SELECT job
      FROM DiaryReminderRescheduleJob job
      WHERE job.status IN :statuses
        AND (job.nextRetryAt IS NULL OR job.nextRetryAt <= :now)
      ORDER BY job.createdAt ASC
      """)
  List<DiaryReminderRescheduleJob> findRunnableJobs(
      @Param("statuses") List<DiaryReminderRescheduleJobStatus> statuses,
      @Param("now") LocalDateTime now);

  @Modifying
  @Query("""
      UPDATE DiaryReminderRescheduleJob job
      SET job.status = 'PROCESSING',
          job.lastError = null,
          job.updatedAt = :now
      WHERE job.id = :jobId
        AND job.status IN :statuses
        AND (job.nextRetryAt IS NULL OR job.nextRetryAt <= :now)
      """)
  int claimRunnableJob(
      @Param("jobId") Long jobId,
      @Param("statuses") List<DiaryReminderRescheduleJobStatus> statuses,
      @Param("now") LocalDateTime now);

  @Modifying
  @Query("""
      UPDATE DiaryReminderRescheduleJob job
      SET job.status = 'RETRY_WAITING',
          job.nextRetryAt = :now,
          job.lastError = 'stale processing job recovered',
          job.updatedAt = :now
      WHERE job.status = 'PROCESSING'
        AND (job.updatedAt IS NULL OR job.updatedAt < :staleBefore)
      """)
  int recoverStaleProcessingJobs(
      @Param("now") LocalDateTime now,
      @Param("staleBefore") LocalDateTime staleBefore);

  @Query("""
      SELECT job
      FROM DiaryReminderRescheduleJob job
      JOIN FETCH job.user
      WHERE job.id = :jobId
      """)
  Optional<DiaryReminderRescheduleJob> findByIdWithUser(@Param("jobId") Long jobId);
}
