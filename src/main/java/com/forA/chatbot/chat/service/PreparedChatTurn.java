package com.forA.chatbot.chat.service;

import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatSession;

public record PreparedChatTurn(ChatSession session, ChatMessage userMessage, ChatMessage botMessage,
        UserProfileDelta profileDelta) { }
