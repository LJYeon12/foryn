package com.forA.chatbot.diary.dto;

import java.util.List;

public record DiaryCompleteRequest(
    Integer emotionScore,
    List<String> tagCodes
) {
}
