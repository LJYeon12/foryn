package com.forA.chatbot.medications.repository;

import com.forA.chatbot.medications.domain.MedicationItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicationItemRepository extends JpaRepository<MedicationItem, Long> {
  List<MedicationItem> findByMedicationBundleId(Long medicationBundleId);

  @Modifying
  @Query("DELETE FROM MedicationItem m WHERE m.medicationBundle.id = :medicationBundleId")
  void deleteByMedicationBundleId(Long medicationBundleId);
}
