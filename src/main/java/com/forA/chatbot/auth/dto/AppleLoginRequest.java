package com.forA.chatbot.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AppleLoginRequest { // 클라이언트가 전송하는 애플 로그인 요청 데이터

  private String identityToken;
  private String authorizationCode;
  private String firstName;
  private String lastName;
}
