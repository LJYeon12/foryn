package com.forA.chatbot.medications.repository;

import com.forA.chatbot.medications.domain.MedicationScheduleOutbox;
import com.forA.chatbot.medications.domain.MedicationScheduleOutbox.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationScheduleOutboxRepository extends JpaRepository<MedicationScheduleOutbox, Long> {

  Optional<MedicationScheduleOutbox> findByBundleIdAndScheduledDateAndScheduleVersion(
      Long bundleId, LocalDate scheduledDate, long scheduleVersion);

  @Query("""
      SELECT DISTINCT job.scheduledDate FROM MedicationScheduleOutbox job
      WHERE job.bundleId = :bundleId AND job.scheduledDate > :today
      """)
  List<LocalDate> findFutureScheduledDates(@Param("bundleId") Long bundleId, @Param("today") LocalDate today);

  @Query("""
      SELECT job.id FROM MedicationScheduleOutbox job
      WHERE job.status IN ('PENDING', 'RETRY_WAITING') AND job.nextAttemptAt <= :now
        AND (:bundleId IS NULL OR job.bundleId = :bundleId)
      ORDER BY job.nextAttemptAt, job.id
      """)
  List<Long> findRunnableIds(@Param("bundleId") Long bundleId, @Param("now") Instant now, Pageable pageable);

  @Modifying
  @Query("""
      UPDATE MedicationScheduleOutbox job
      SET job.status = 'PROCESSING', job.attempts = job.attempts + 1,
          job.lockOwner = :owner, job.lockUntil = :until, job.lastError = null
      WHERE job.id = :id AND job.status IN ('PENDING', 'RETRY_WAITING')
        AND job.nextAttemptAt <= :now AND job.attempts < :maxAttempts
      """)
  int claim(@Param("id") Long id, @Param("owner") String owner, @Param("now") Instant now,
      @Param("until") Instant until, @Param("maxAttempts") int maxAttempts);

  @Modifying
  @Query("""
      UPDATE MedicationScheduleOutbox job
      SET job.status = :status, job.nextAttemptAt = :nextAttemptAt,
          job.lastError = :error, job.lockOwner = null, job.lockUntil = null
      WHERE job.id = :id AND job.status = 'PROCESSING' AND job.lockOwner = :owner
      """)
  int finish(@Param("id") Long id, @Param("owner") String owner, @Param("status") Status status,
      @Param("nextAttemptAt") Instant nextAttemptAt, @Param("error") String error);

  @Modifying
  @Query("""
      UPDATE MedicationScheduleOutbox job
      SET job.status = 'RETRY_WAITING', job.nextAttemptAt = :now,
          job.lockOwner = null, job.lockUntil = null, job.lastError = 'worker_lease_expired'
      WHERE job.status = 'PROCESSING' AND job.lockUntil <= :now AND job.attempts < :maxAttempts
      """)
  int recoverExpiredClaims(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

  @Modifying
  @Query("""
      UPDATE MedicationScheduleOutbox job
      SET job.status = 'FAILED', job.nextAttemptAt = null,
          job.lockOwner = null, job.lockUntil = null, job.lastError = 'worker_lease_exhausted'
      WHERE job.status = 'PROCESSING' AND job.lockUntil <= :now AND job.attempts >= :maxAttempts
      """)
  int failExhaustedClaims(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

  @Modifying
  @Query("""
      UPDATE MedicationScheduleOutbox job
      SET job.status = 'PENDING', job.attempts = 0, job.nextAttemptAt = :now, job.lastError = null
      WHERE job.id = :id AND job.status = 'FAILED'
      """)
  int retryFailed(@Param("id") Long id, @Param("now") Instant now);
}
