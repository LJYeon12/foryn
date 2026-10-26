package com.forA.chatbot.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Slf4j
@Configuration
public class FirebaseConfig {

  // 1. 운영용: ECS 환경변수에서 주입받을 Base64 문자열
  @Value("${firebase.key.base64:#{null}}")
  private String firebaseKeyBase64;

  // 2. 개발용: resources 폴더에 있는 파일명
  @Value("${firebase.key.path:mori-firebase-key.json}")
  private String firebaseKeyPath;

  @PostConstruct
  public void init() {
    log.info("🚀 [FCM] Firebase 초기화 시작 (JSON 자동 추출 방식)");
    try {
      InputStream serviceAccountStream = StringUtils.hasText(firebaseKeyBase64)
          ? new ByteArrayInputStream(Base64.getDecoder().decode(firebaseKeyBase64))
          : new ClassPathResource(firebaseKeyPath).getInputStream();

      // 1. 문서의 권장 범위를 적용하여 인증 정보 생성
      GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
          .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));

      // 2. JSON 파일에서 프로젝트 ID 직접 추출
      String projectId = null;
      if (credentials instanceof ServiceAccountCredentials) {
        projectId = ((ServiceAccountCredentials) credentials).getProjectId();
      }

      // 3. 추출한 ID가 null일 경우를 대비한 방어 로직
      if (projectId == null) {
        log.error("❌ [FCM] JSON 파일에서 Project ID를 읽어오지 못했습니다. 파일 내용을 확인하세요.");
        return;
      }

      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(credentials)
          .setProjectId(projectId) // 자동으로 읽어온 ID 주입
          .setConnectTimeout(5_000)   // 5초 — 오래된 연결을 빠르게 감지
          .setReadTimeout(10_000)     // 10초 — 기존 80초 블로킹 방지
          .build();

      if (!FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.getInstance().delete();
        log.info("⚠️ [FCM] 기존의 잘못된 Firebase 인스턴스를 삭제하고 재초기화합니다.");
      }

      FirebaseApp.initializeApp(options);


      log.info("🎉 [FCM] 초기화 성공! 프로젝트 ID: {}", FirebaseApp.getInstance().getOptions().getProjectId());
    } catch (Exception e) {
      log.error("❌ [FCM] 초기화 실패", e);
    }
  }
}
