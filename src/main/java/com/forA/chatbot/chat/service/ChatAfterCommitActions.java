package com.forA.chatbot.chat.service;

import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.notification.scheduler.ChatNotificationScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatAfterCommitActions {
    private final ChatNotificationScheduler notifications;
    private final MeterRegistry metrics;

    public void run(PreparedChatTurn turn) {
        try {
            String from = turn.userMessage().getChatStep();
            if (ChatStep.SKILL_SELECT.name().equals(from)) {
                notifications.scheduleNotification(turn.session().getId(), turn.session().getUserId(),
                    turn.session().getTemporaryData("selectedSkillName"));
            } else if (ChatStep.SKILL_CONFIRM.name().equals(from)) {
                notifications.cancelNotification(turn.session().getId());
            }
        } catch (RuntimeException failure) {
            metrics.counter("chat.after.commit.failures").increment();
        }
    }
}
