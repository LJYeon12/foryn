package com.forA.chatbot.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "chat.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class ChatRecoveryScheduler {
    private final ChatRecoveryService recovery;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${chat.recovery.poll-delay-ms:10000}", initialDelayString = "${chat.recovery.poll-delay-ms:10000}")
    public void recover() {
        try {
            recovery.recoverDue();
        } catch (RuntimeException failure) {
            log.warn("Chat recovery scan unavailable; failureType={}", failure.getClass().getSimpleName());
        }
    }
}
