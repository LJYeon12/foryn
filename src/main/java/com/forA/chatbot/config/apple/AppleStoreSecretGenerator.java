package com.forA.chatbot.config.apple;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Date;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppleStoreSecretGenerator { // 인 앱 구독용
    private final AppleKeyLoader appleKeyLoader;
    @Value("${apple.store.issuer-id}")
    private String issuerId;

    @Value("${apple.store.key-id}")
    private String keyId;

    @Value("${apple.store.bundle-id}")
    private String bundleId;

    @Value("${apple.store.key.path}")
    private String keyPath;

    @Value("${apple.store.key.base64:#{null}}")
    private String keyBase64;

    public String generate() throws IOException {
        PrivateKey privateKey = appleKeyLoader.loadPrivateKey(keyBase64, keyPath);

        // JJWT 0.12.x 최신 API 적용
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .issuer(issuerId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + (1000 * 60 * 5)))
                .audience()
                .add("appstoreconnect-v1")
                .and()
                .claim("bid", bundleId) // 인앱 결제 API 필수 클레임
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }
}
