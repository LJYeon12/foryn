package com.forA.chatbot.medications.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MedicationBundleChangedEvent {

  private final Long bundleId;
  private final ChangeType changeType;

  public enum ChangeType {
    CREATED, UPDATED, DELETED
  }
}
