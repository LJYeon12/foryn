package com.forA.chatbot.enums;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.MedicationHandler;

public enum DayOfWeek {
  MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

  public static DayOfWeek from(String value) {
    try {
      return DayOfWeek.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new MedicationHandler(ErrorStatus.MEDICATION_INVALID_DAY_OF_WEEK);
    }
  }
}