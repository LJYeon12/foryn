package com.forA.chatbot.notification.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StreakAchievementEvent {
    private final Long userId;
}
