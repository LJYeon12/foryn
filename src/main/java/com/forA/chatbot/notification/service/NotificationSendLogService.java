package com.forA.chatbot.notification.service;

import com.forA.chatbot.notification.domain.NotificationSendLog;
import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.repository.NotificationSendLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class NotificationSendLogService {

  public enum ClaimResult {
    CLAIMED,
    ALREADY_SENT,
    LOCKED_BY_ACTIVE_PROCESSOR,
    RETRY_REQUIRED
  }

  private final NotificationSendLogRepository sendLogRepository;
  private final TransactionTemplate requiresNewTransaction;

  public NotificationSendLogService(
      NotificationSendLogRepository sendLogRepository,
      PlatformTransactionManager transactionManager) {
    this.sendLogRepository = sendLogRepository;
    this.requiresNewTransaction = new TransactionTemplate(transactionManager);
    this.requiresNewTransaction.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public ClaimResult claim(Long bundleId, Long deviceTokenId, LocalDate scheduledDate,
      LocalDateTime lockUntil) {
    return claim(NotificationType.MEDICATION, bundleId, deviceTokenId, scheduledDate, lockUntil);
  }

  public ClaimResult claim(NotificationType notificationType, Long targetId, Long deviceTokenId,
      LocalDate scheduledDate, LocalDateTime lockUntil) {
    Boolean inserted = requiresNewTransaction.execute(status -> {
      try {
        sendLogRepository.saveAndFlush(NotificationSendLog.builder()
            .notificationType(notificationType)
            .targetId(targetId)
            .deviceTokenId(deviceTokenId)
            .scheduledDate(scheduledDate)
            .status("PROCESSING")
            .lockUntil(lockUntil)
            .build());
        return true;
      } catch (DataIntegrityViolationException e) {
        status.setRollbackOnly();
        return false;
      }
    });

    if (Boolean.TRUE.equals(inserted)) {
      return ClaimResult.CLAIMED;
    }

    Integer updated = requiresNewTransaction.execute(status ->
        sendLogRepository.claimExisting(
            notificationType, targetId, deviceTokenId, scheduledDate, lockUntil,
            LocalDateTime.now()));

    if (updated != null && updated > 0) {
      log.info("기존 발송 로그를 다시 선점했습니다. type={}, targetId={}, deviceTokenId={}, date={}",
          notificationType, targetId, deviceTokenId, scheduledDate);
      return ClaimResult.CLAIMED;
    }

    NotificationSendLog existingLog = requiresNewTransaction.execute(status ->
        sendLogRepository.findByNotificationTypeAndTargetIdAndScheduledDateAndDeviceTokenId(
                notificationType, targetId, scheduledDate, deviceTokenId)
            .orElse(null));

    if (existingLog == null) {
      log.warn(
          "발송 로그 선점 재시도 필요: row 없음. type={}, targetId={}, deviceTokenId={}, date={}",
          notificationType, targetId, deviceTokenId, scheduledDate);
      return ClaimResult.RETRY_REQUIRED;
    }

    if (existingLog.isSent()) {
      log.info("이미 발송 완료된 로그입니다. type={}, targetId={}, deviceTokenId={}, date={}",
          notificationType, targetId, deviceTokenId, scheduledDate);
      return ClaimResult.ALREADY_SENT;
    }

    if (existingLog.isLockActive()) {
      log.info("활성 lock으로 발송 로그 선점이 막혔습니다. type={}, targetId={}, deviceTokenId={}, date={}",
          notificationType, targetId, deviceTokenId, scheduledDate);
      return ClaimResult.LOCKED_BY_ACTIVE_PROCESSOR;
    }

    log.warn(
        "발송 로그 상태가 애매해 재시도합니다. type={}, targetId={}, deviceTokenId={}, date={}, status={}",
        notificationType, targetId, deviceTokenId, scheduledDate, existingLog.getStatus());
    return ClaimResult.RETRY_REQUIRED;
  }

  public void markSent(Long bundleId, Long deviceTokenId, LocalDate scheduledDate,
      LocalDateTime sentAt) {
    markSent(NotificationType.MEDICATION, bundleId, deviceTokenId, scheduledDate, sentAt);
  }

  public void markSent(NotificationType notificationType, Long targetId, Long deviceTokenId,
      LocalDate scheduledDate, LocalDateTime sentAt) {
    Integer updated = requiresNewTransaction.execute(status ->
        sendLogRepository.markSent(notificationType, targetId, deviceTokenId, scheduledDate,
            sentAt));

    if (updated == null || updated == 0) {
      log.warn("발송 완료 처리 대상이 없습니다. type={}, targetId={}, deviceTokenId={}, date={}",
          notificationType, targetId, deviceTokenId, scheduledDate);
    }
  }

  public void releaseClaim(Long bundleId, Long deviceTokenId, LocalDate scheduledDate,
      String errorMessage) {
    releaseClaim(NotificationType.MEDICATION, bundleId, deviceTokenId, scheduledDate,
        errorMessage);
  }

  public void releaseClaim(NotificationType notificationType, Long targetId, Long deviceTokenId,
      LocalDate scheduledDate, String errorMessage) {
    Integer updated = requiresNewTransaction.execute(status ->
        sendLogRepository.releaseClaim(notificationType, targetId, deviceTokenId, scheduledDate,
            errorMessage));

    if (updated == null || updated == 0) {
      log.warn("발송 lock 해제 대상이 없습니다. type={}, targetId={}, deviceTokenId={}, date={}",
          notificationType, targetId, deviceTokenId, scheduledDate);
    }
  }
}
