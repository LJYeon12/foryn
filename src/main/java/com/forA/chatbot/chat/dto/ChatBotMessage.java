package com.forA.chatbot.chat.dto;

import com.forA.chatbot.chat.domain.enums.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ChatBotMessage {
    private String content;
    private MessageType type;
    private List<ButtonOption> options;
}
