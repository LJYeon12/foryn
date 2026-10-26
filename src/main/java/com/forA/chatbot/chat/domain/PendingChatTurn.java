package com.forA.chatbot.chat.domain;

public record PendingChatTurn(String operationId, long expectedVersion, ChatSessionState state) { }
