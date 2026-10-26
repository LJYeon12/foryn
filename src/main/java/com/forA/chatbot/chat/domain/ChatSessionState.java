package com.forA.chatbot.chat.domain;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public record ChatSessionState(String currentStep, Boolean onboardingCompleted,
        Map<String, String> temporaryData, LocalDateTime endedAt, LocalDateTime lastInteractionAt) {
    public ChatSessionState {
        temporaryData = temporaryData == null ? new HashMap<>() : new HashMap<>(temporaryData);
    }

    public static ChatSessionState from(ChatSession session) {
        return new ChatSessionState(session.getCurrentStep(), session.getOnboardingCompleted(),
            session.getTemporaryData(), session.getEndedAt(), session.getLastInteractionAt());
    }
}
