package com.forA.chatbot.medications.domain;

import com.forA.chatbot.global.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "medication_logs")
public class MedicationLog extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "medication_bundle_id")
  private MedicationBundle medicationBundle;

  @Column(nullable = false)
  private LocalDate date;

  @Column(name = "is_taken", nullable = false)
  private Boolean isTaken;

  @Column(length = 50, name = "med_condition")
  private Integer medCondition;

  @Builder
  public MedicationLog(
      MedicationBundle medicationBundle, LocalDate date, Boolean isTaken, Integer medCondition) {
    this.medicationBundle = medicationBundle;
    this.date = date;
    this.isTaken = isTaken;
    this.medCondition = medCondition;
  }
}
