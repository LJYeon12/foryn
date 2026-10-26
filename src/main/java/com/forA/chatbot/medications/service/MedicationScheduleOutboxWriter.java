package com.forA.chatbot.medications.service;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationScheduleOutbox;
import com.forA.chatbot.medications.event.MedicationBundleChangedEvent;
import com.forA.chatbot.medications.event.MedicationBundleChangedEvent.ChangeType;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationScheduleOutboxRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicationScheduleOutboxWriter {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private final MedicationScheduleOutboxRepository outboxRepository;
  private final MedicationBundleRepository bundleRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordChange(MedicationBundle bundle) {
    LocalDate today = LocalDate.now(KST);
    appendIfMissing(bundle, today);
    // 오늘 일정과 향후 일일 배치가 이미 등록한 날짜를 함께 갱신한다.
    for (LocalDate date : outboxRepository.findFutureScheduledDates(bundle.getId(), today)) {
      appendIfMissing(bundle, date);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDaily(Long bundleId, LocalDate date) {
    // 같은 계획의 중복 배치 호출을 직렬화한다. 입출력 중에는 잠금을 유지하지 않는다.
    bundleRepository.findByIdForUpdate(bundleId).ifPresent(bundle -> {
      appendIfMissing(bundle, date);
      eventPublisher.publishEvent(new MedicationBundleChangedEvent(bundleId, ChangeType.UPDATED));
    });
  }

  private void appendIfMissing(MedicationBundle bundle, LocalDate date) {
    if (outboxRepository.findByBundleIdAndScheduledDateAndScheduleVersion(
        bundle.getId(), date, bundle.getScheduleVersion()).isEmpty()) {
      outboxRepository.saveAndFlush(new MedicationScheduleOutbox(bundle, date, Instant.now()));
    }
  }
}
