package com.forA.chatbot.auth.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApplePublicKeyResponse {

  private List<ApplePublicKey> keys;

  /*
   *   - Apple API (https://appleid.apple.com/auth/keys)의 응답을 매핑
   * 여러 개의 공개키가 배열로 제공됨
   * 각 키는 서로 다른 kid를 가짐
   *
   */
}
