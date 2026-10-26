package com.forA.chatbot.notification.repository;

import com.forA.chatbot.notification.domain.NotificationSendLog;
import com.forA.chatbot.notification.domain.NotificationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationSendLogRepository extends JpaRepository<NotificationSendLog, Long> {

  Optional<NotificationSendLog> findByNotificationTypeAndTargetIdAndScheduledDateAndDeviceTokenId(
      NotificationType notificationType, Long targetId, LocalDate scheduledDate,
      Long deviceTokenId);

  @Modifying
  @Query("UPDATE NotificationSendLog l SET l.status = 'SENT', l.sentAt = :sentAt, "
      + "l.lockUntil = null WHERE l.notificationType = :notificationType "
      + "AND l.targetId = :targetId "
      + "AND l.scheduledDate = :scheduledDate AND l.deviceTokenId = :deviceTokenId "
      + "AND l.status = 'PROCESSING'")
  int markSent(@Param("notificationType") NotificationType notificationType,
      @Param("targetId") Long targetId,
      @Param("deviceTokenId") Long deviceTokenId,
      @Param("scheduledDate") LocalDate scheduledDate,
      @Param("sentAt") LocalDateTime sentAt);

  @Modifying
  @Query("UPDATE NotificationSendLog l SET l.status = 'PENDING', l.lockUntil = null, "
      + "l.lastError = :errorMessage WHERE l.notificationType = :notificationType "
      + "AND l.targetId = :targetId "
      + "AND l.scheduledDate = :scheduledDate AND l.deviceTokenId = :deviceTokenId "
      + "AND l.status = 'PROCESSING'")
  int releaseClaim(@Param("notificationType") NotificationType notificationType,
      @Param("targetId") Long targetId,
      @Param("deviceTokenId") Long deviceTokenId,
      @Param("scheduledDate") LocalDate scheduledDate,
      @Param("errorMessage") String errorMessage);

  @Modifying
  @Query("UPDATE NotificationSendLog l SET l.status = 'PROCESSING', "
      + "l.lockUntil = :lockUntil, l.lastError = null "
      + "WHERE l.notificationType = :notificationType AND l.targetId = :targetId "
      + "AND l.scheduledDate = :scheduledDate "
      + "AND l.deviceTokenId = :deviceTokenId "
      + "AND l.status != 'SENT' "
      + "AND (l.status != 'PROCESSING' OR l.lockUntil < :now)")
  int claimExisting(@Param("notificationType") NotificationType notificationType,
      @Param("targetId") Long targetId,
      @Param("deviceTokenId") Long deviceTokenId,
      @Param("scheduledDate") LocalDate scheduledDate,
      @Param("lockUntil") LocalDateTime lockUntil,
      @Param("now") LocalDateTime now);
}
