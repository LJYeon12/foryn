package com.forA.chatbot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResetResponse {

  private String message;

  public static UserResetResponse success() {
    return new UserResetResponse("User data has been successfully reset.");
  }
}
