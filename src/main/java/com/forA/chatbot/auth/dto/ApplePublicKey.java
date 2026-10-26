package com.forA.chatbot.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApplePublicKey {

  // 애플이 제공하는 공개키 정보를 담는 DTO -> JWT 토큰 서명 검증에 사용
  private String kty; // 키 유형
  private String kid; // 토큰 헤더의 kid와 매칭하여 올바른 공개키 선택
  private String use; // 공개키 용도
  private String alg; // 알고리즘
  private String n; // RSA 모듈러스
  private String e; // RSA 지수
}
