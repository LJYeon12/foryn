package com.forA.chatbot.apiPayload.exception.handler;

import com.forA.chatbot.apiPayload.code.BaseErrorCode;
import com.forA.chatbot.apiPayload.exception.GeneralException;

public class UserHandler extends GeneralException {

  public UserHandler(BaseErrorCode code) {
    super(code);
  }
}
