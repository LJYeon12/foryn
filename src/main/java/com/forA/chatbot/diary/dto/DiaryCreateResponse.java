package com.forA.chatbot.diary.dto;

public record DiaryCreateResponse(
    Long diaryId,
    String status,
    String forynComment,
    int todayCreatedDiaryCount,
    int remainingDiaryCount
) {}

