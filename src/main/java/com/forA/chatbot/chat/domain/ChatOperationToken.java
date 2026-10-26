package com.forA.chatbot.chat.domain;

public record ChatOperationToken(String id, Long userId, String sessionId, long fence) { }
