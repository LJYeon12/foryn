package com.forA.chatbot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserDeleteResponse {

  private String message;

  public static UserDeleteResponse success() {
    return new UserDeleteResponse(
        "계정이 정상적으로 삭제가 완료되었습니다.");
  }
}
