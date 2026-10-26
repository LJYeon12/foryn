package com.forA.chatbot.auth.client;

import com.forA.chatbot.auth.dto.ApplePublicKeyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "apple-auth", url = "https://appleid.apple.com")
public interface AppleAuthClient {

  @GetMapping("/auth/keys")
  ApplePublicKeyResponse getPublicKeys(); // 애플 공개키 목록을 조회하는 메서드
  // 애플 서버에 GET요청 전송 -> Json응답을 ApplePublicKeyResponse 객체로 자동 변환 -> JWT 토큰 검증 시 사용할 공개키 정보 반환
}
