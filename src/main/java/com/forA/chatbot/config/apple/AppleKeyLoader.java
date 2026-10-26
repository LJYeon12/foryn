package com.forA.chatbot.config.apple;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
@Component
public class AppleKeyLoader {

    public PrivateKey loadPrivateKey(String keyBase64, String keyPath) throws IOException {
        Reader reader;
        // Base64 키가 있으면 그걸 쓰고, 없으면 파일을 읽는다.
        if (StringUtils.hasText(keyBase64)) {
            // 운영 환경: Base64 문자열 -> 디코딩 -> StringReader (.p8 파일 전체를 Base64로 인코딩했다고 가정)
            byte[] decodedBytes = Base64.getDecoder().decode(keyBase64);
            reader = new StringReader(new String(decodedBytes));
        } else {
            ClassPathResource resource = new ClassPathResource(keyPath.replace("classpath:", ""));
            if (!resource.exists()) {
                throw new IOException("Apple Key File not found: " + keyPath);
            }
            reader = new InputStreamReader(resource.getInputStream());
        }

        try (PemReader pemReader = new PemReader(reader)) {
            PemObject pemObject = pemReader.readPemObject();
            if (pemObject == null)
                throw new RuntimeException("Apple Key PEM 파싱 실패");

            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pemObject.getContent());
            KeyFactory kf = KeyFactory.getInstance("EC");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("PrivateKey 생성 중 오류 발생", e);
        }
    }
}
