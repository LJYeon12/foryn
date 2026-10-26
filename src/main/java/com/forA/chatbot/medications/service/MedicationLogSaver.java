package com.forA.chatbot.medications.service;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MedicationLogSaver {

  private final MedicationLogRepository medicationLogRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MedicationLog saveNewLog(MedicationLog medicationLog) {
    return medicationLogRepository.saveAndFlush(medicationLog);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public Optional<MedicationLog> findExistingLogAfterDuplicate(
      MedicationBundle medicationBundle, LocalDate date) {
    return medicationLogRepository.findByMedicationBundleAndDate(medicationBundle, date);
  }
}
