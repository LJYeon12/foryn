package com.forA.chatbot.auth.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.AuthHandler;
import com.forA.chatbot.auth.dto.ApplePublicKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppleTokenValidator {

  private final ApplePublicKeyService applePublicKeyService;

  // 클레임: JWT 토큰의 페이로드에 담긴 정보
  public Claims validateToken(String identityToken) { // 토큰 검증 로직
    try {
      // 1. JWT 헤더에서 kid 추출
      String[] tokenParts = identityToken.split("\\.");
      String header = new String(Base64.getUrlDecoder().decode(tokenParts[0]));

      // 2. kid로 적절한 애플 공개키 찾기 (애플 서버에서 받은 공개키가 서버에 존재하는지 확인)
      String kid = extractKidFromHeader(header);
      ApplePublicKey applePublicKey = findPublicKeyByKid(kid);

      // 3. RSA 공개키 생성
      PublicKey publicKey = generateRSAPublicKey(applePublicKey);

      // 4. JWT 토큰 검증
      return Jwts.parser()
          .verifyWith(publicKey)
          .build()
          .parseSignedClaims(identityToken)
          .getPayload();

    } catch (Exception e) {
      log.error("Apple token validation failed", e);
      throw new AuthHandler(ErrorStatus.APPLE_TOKEN_INVALID);
    }
  }

  private String extractKidFromHeader(String header) { // 키 ID 추출
    // JSON을 파싱하여 kid 추출
    int kidIndex = header.indexOf("\"kid\":\"") + 7;
    int endIndex = header.indexOf("\"", kidIndex);
    return header.substring(kidIndex, endIndex);
  }

  private ApplePublicKey findPublicKeyByKid(String kid) {
    List<ApplePublicKey> keys = applePublicKeyService.getApplePublicKeys();
    return keys.stream()
        .filter(key -> kid.equals(key.getKid()))
        .findFirst()
        .orElseThrow(() -> new AuthHandler(ErrorStatus.APPLE_PUBLIC_KEY_ERROR));
  }

  private PublicKey generateRSAPublicKey(ApplePublicKey applePublicKey)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    byte[] nBytes = Base64.getUrlDecoder().decode(applePublicKey.getN());
    byte[] eBytes = Base64.getUrlDecoder().decode(applePublicKey.getE());

    BigInteger modulus = new BigInteger(1, nBytes);
    BigInteger exponent = new BigInteger(1, eBytes);

    RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(spec);
  }
}
