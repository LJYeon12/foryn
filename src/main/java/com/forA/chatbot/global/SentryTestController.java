package com.forA.chatbot.global;

import io.sentry.Sentry;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/sentry-test")
public class SentryTestController {

    @GetMapping("/error")
    public String triggerError() {
        throw new RuntimeException("Sentry 연동 테스트 에러!");
    }

    @GetMapping("/capture")
    public String captureManual() {
        try {
            throw new Exception("Sentry 수동 캡처 테스트");
        } catch (Exception e) {
            Sentry.captureException(e);
        }
        return "Sentry에 이벤트가 전송되었습니다. 대시보드를 확인하세요.";
    }
}
