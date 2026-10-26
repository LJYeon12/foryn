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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "medication_items")
public class MedicationItem extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "medication_bundle_id", nullable = false)
  private MedicationBundle medicationBundle;

  @Column(name = "medication_name", nullable = false, length = 50)
  private String medicationName;

  @Builder
  public MedicationItem(MedicationBundle medicationBundle, String medicationName) {
    this.medicationBundle = medicationBundle;
    this.medicationName = medicationName;
  }
}
