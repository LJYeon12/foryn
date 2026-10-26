package com.forA.chatbot.apiPayload.exception.handler;

import com.forA.chatbot.apiPayload.code.BaseErrorCode;
import com.forA.chatbot.apiPayload.exception.GeneralException;

public class NotificationHandler extends GeneralException {

  public NotificationHandler(BaseErrorCode code) {
    super(code);
  }
}
