package com.forA.chatbot.chat.dto;

import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ChatMessageDto {
    private String sender;
    private String content;
    private MessageType type;
    private ChatStep step;
    private List<ButtonOption> options;
    private LocalDateTime sentAt;
}
