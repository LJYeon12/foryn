package com.forA.chatbot.chat.domain;

public record ChatOperation(ChatOperationToken token, String kind, String status, boolean sqlCommitted,
        int attempts, long leaseUntil, long nextAttemptAt) {
    public boolean terminal() { return status.equals("COMPLETED") || status.equals("CANCELLED"); }
}
