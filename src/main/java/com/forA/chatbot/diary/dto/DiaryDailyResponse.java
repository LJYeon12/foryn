package com.forA.chatbot.diary.dto;

import java.util.List;

public record DiaryDailyResponse(
    String date,
    boolean hasWritten,
    int diaryCount,
    boolean canWriteMore,
    int maxDiaryCount,
    int todayDiaryCount,
    int remainingDiaryCount,
    DraftDiaryItem draftDiary,
    List<DiaryItem> diaries
) {

  public record DraftDiaryItem(
      Long diaryId,
      String status,
      String createdAt,
      List<AnswerItem> answers,
      String forynComment
  ) {
  }

  public record DiaryItem(
      Long diaryId,
      int recordOrder,
      String createdAt,
      List<AnswerItem> answers,
      String forynComment,
      Integer emotionScore,
      List<TagItem> tags
  ) {
  }

  public record AnswerItem(
      int questionId,
      String question,
      String answer
  ) {
  }

  public record TagItem(
      String tagType,
      String tagCode,
      String label
  ) {
  }
}
