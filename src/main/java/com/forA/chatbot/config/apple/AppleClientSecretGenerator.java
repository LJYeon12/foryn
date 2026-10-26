package com.forA.chatbot.config.apple;

import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class AppleClientSecretGenerator {

  private final AppleKeyLoader appleKeyLoader;
  @Value("${apple.team-id}")
  private String teamId;

  @Value("${apple.key-id}")
  private String keyId;

  @Value("${apple.client-id}")
  private String clientId;

  // 1. 운영용: Base64로 인코딩된 키 값 (ECS 환경변수)
  @Value("${apple.key.base64:#{null}}")
  private String keyBase64;

  // 2. 개발용: 키 파일 경로 (로컬 파일)
  @Value("${apple.key.path:AuthKey.p8}")
  private String keyPath;

  public String generate() throws IOException {
    PrivateKey privateKey = appleKeyLoader.loadPrivateKey(keyBase64, keyPath);
    // JJWT 0.12.x 최신 API 적용
    return Jwts.builder()
        .header()
        .keyId(keyId)
        .and()
        .issuer(teamId)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + (1000 * 60 * 5)))
        .audience()
        .add("https://appleid.apple.com")
        .and()
        .subject(clientId)
        .signWith(privateKey, Jwts.SIG.ES256)
        .compact();
  }
}
