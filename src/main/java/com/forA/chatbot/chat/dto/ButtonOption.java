package com.forA.chatbot.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ButtonOption {
    private String label;
    private String emoji;
    private boolean multiSelect;
    private String group;
}
