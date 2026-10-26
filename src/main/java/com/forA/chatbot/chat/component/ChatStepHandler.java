package com.forA.chatbot.chat.component;

import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.user.domain.User;

public interface ChatStepHandler {
    String getStepName();

    String handleRequest(ChatSession session, User user, ChatRequest request);

    ChatBotMessage getBotMessage(ChatSession session, User user);
    // 자가 치유를 위한 상태 검증 로직
    default void validateState(ChatSession session, User user) {}
}
