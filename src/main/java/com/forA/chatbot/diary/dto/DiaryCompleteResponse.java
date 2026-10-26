package com.forA.chatbot.diary.dto;

public record DiaryCompleteResponse(
    Long diaryId,
    String status,
    int todayCompletedDiaryCount,
    int remainingDiaryCount,
    String completionMessage
) {
}
