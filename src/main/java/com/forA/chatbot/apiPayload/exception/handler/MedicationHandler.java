package com.forA.chatbot.apiPayload.exception.handler;

import com.forA.chatbot.apiPayload.code.BaseErrorCode;
import com.forA.chatbot.apiPayload.exception.GeneralException;

public class MedicationHandler extends GeneralException {
  public MedicationHandler(BaseErrorCode code) {
    super(code);
  }
}
