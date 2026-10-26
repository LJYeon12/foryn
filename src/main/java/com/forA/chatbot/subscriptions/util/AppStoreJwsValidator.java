package com.forA.chatbot.subscriptions.util;

import com.forA.chatbot.apiPayload.exception.handler.SubscriptionHandler;
import com.forA.chatbot.subscriptions.dto.DecodedNotificationPayload;
import com.forA.chatbot.subscriptions.dto.DecodedSignedRenewalInfo;
import com.forA.chatbot.subscriptions.dto.DecodedSignedTransactionInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
/**
 * App Store Server API의 JWS 응답을 검증하고 디코딩하는 유틸리티
 */
@Slf4j
@Component
public class AppStoreJwsValidator {
  private final ObjectMapper objectMapper;
  private final Set<TrustAnchor> appleRootCAs; // 애플 루트 인증서 저장소
  public AppStoreJwsValidator(ObjectMapper objectMapper,
      @Value("classpath:certs/AppleRootCA-G3.cer") Resource appleRootCaG3) {
    this.objectMapper = objectMapper;
    this.appleRootCAs = new HashSet<>();
    try (InputStream is = appleRootCaG3.getInputStream()) {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate rootCert = (X509Certificate) cf.generateCertificate(is);
      this.appleRootCAs.add(new TrustAnchor(rootCert, null));
      log.info("Apple Root CA - G3 인증서 로드 성공");
    } catch (Exception e) {
      log.error("Apple Root CA 인증서 로드 실패. certs/AppleRootCA-G3.pem 파일을 확인하세요.", e);
      // 서버 시작 시 인증서 로드에 실패하면 치명적 오류이므로 RuntimeException 발생
      throw new RuntimeException("Apple Root CA 로드 실패", e);
    }
  }

  public DecodedSignedTransactionInfo decodeSignedTransaction(String jws) {
    Claims claims = verifyAndDecodeJws(jws);
    return objectMapper.convertValue(claims, DecodedSignedTransactionInfo.class);
  }
  public DecodedSignedRenewalInfo decodeRenewalInfo(String jws) {
    Claims claims = verifyAndDecodeJws(jws);
    return objectMapper.convertValue(claims, DecodedSignedRenewalInfo.class);
  }

  // Apple 서버 알림 signedPayload JWS 검증 및 디코딩
  public DecodedNotificationPayload decodeNotificationPayload(String jws) {
    Claims claims = verifyAndDecodeJws(jws); // // 1. JWS 검증 및 클레임 추출
    try {
      return objectMapper.convertValue(claims, DecodedNotificationPayload.class);
    } catch (Exception e) {
      log.error("JWS Claims를 DecodedNotificationPayload DTO로 변환 실패", e);
      throw new SubscriptionHandler(ErrorStatus.IAP_APPLE_INVALID_TRANSACTION);
    }

  }
  /**
  * JWS를 파싱하고 헤더의 x5c 인증서 체인을 검증한 뒤 페이로드(클레임)를 반환한다. 클레임은 JWT 내용이다.
  * */
  private Claims verifyAndDecodeJws(String jws) {
    try {
      // 1. JWS 파서에 'SigningKeyResolver'를 설정
      //    리졸버가 JWS 헤더를 보고 올바른 공개키를 찾는다.
      return Jwts.parser()
          .setSigningKeyResolver(new X5cSigningKeyResolver(this.appleRootCAs))
          .build()
          .parseClaimsJws(jws)
          .getBody();
    } catch (Exception e) {
      log.error("Apple JWS 검증 또는 디코딩 실패. JWS(앞20자): {}",
          jws != null && jws.length() > 20 ? jws.substring(0, 20) + "..." : jws, e);
      throw new SubscriptionHandler(ErrorStatus.APPLE_TOKEN_INVALID);
    }

  }

  /**
   * JWS 헤더의 x5c 인증서 체인을 검증한 뒤 서명 키(공개키)를 반환하는 리졸버
   * */
  private static class X5cSigningKeyResolver implements SigningKeyResolver {
    private final Set<TrustAnchor> trustAnchors;
    private final CertificateFactory cf;
    public X5cSigningKeyResolver(Set<TrustAnchor> appleRootCAs) {
      this.trustAnchors = appleRootCAs;
      try {
        this.cf = CertificateFactory.getInstance("X.509");
      } catch (CertificateException e) {
        throw new SubscriptionHandler(ErrorStatus.FAILED_TO_CREATE_CERTIFICATE_FACTORY);
      }
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, Claims claims) {
      return getPublicKey(header); // JWS (페이로드)
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, byte[] content) {
      return getPublicKey(header);
    }

    private PublicKey getPublicKey(JwsHeader header) {
      try {
        // 1. JWS 헤더에서 'x5c' (Base64 인코딩된 인증서 체인) 추출
        List<String> chain = (List<String>) header.get("x5c");
        if (chain == null || chain.isEmpty()) {
          throw new IllegalArgumentException("JWS 헤더에 'x5c' 인증서 체인이 없습니다.");
        }
        // 2. Base64 문자열을 X509Certificate 객체 리스트로 변환
        List<X509Certificate> certs = chain.stream()
            .map(s -> {
              try {
                byte[] bytes = Base64.getDecoder().decode(s);
                return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bytes));
              } catch (Exception e) {
                throw new RuntimeException("x5c 인증서 파싱 실패", e);
              }
            })
            .toList();

        // 3. 인증서 체인 검증
        //    이 체인이 우리가 신뢰하는 'trustAnchors'(Apple Root CA)에서 발급된 것인지 확인
        CertPath certPath = cf.generateCertPath(certs);
        PKIXParameters params = new PKIXParameters(this.trustAnchors);
        params.setRevocationEnabled(false);

        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        validator.validate(certPath, params); // ★ 검증 실패 시 예외 발생
        return certs.get(0).getPublicKey();
      } catch (Exception e) {
        log.error("JWS 'x5c' 헤더 처리 중 오류 발생", e);
        throw new JwtException("JWS x5c 인증서 체인 검증 실패", e);
      }
    }
  }
}
