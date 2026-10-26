package com.forA.chatbot.apiPayload.exception.handler;

import com.forA.chatbot.apiPayload.code.BaseErrorCode;
import com.forA.chatbot.apiPayload.exception.GeneralException;

public class SubscriptionHandler extends GeneralException {

  public SubscriptionHandler(BaseErrorCode code) {
    super(code);
  }
}
