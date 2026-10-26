package com.forA.chatbot.diary.domain.enums;

import java.util.Arrays;

public enum DiaryQuestion {
  MOMENT(1, "오늘 가장 기억 남는 순간은?"),
  THOUGHT(2, "그 순간, 머릿속에 어떤 생각이 스쳤나요?"),
  EMOTION(3, "그 때 내 감정은 어땠나요?");

  private final int id;
  private final String question;

  DiaryQuestion(int id, String question) {
    this.id = id;
    this.question = question;
  }

  public int getId() {
    return id;
  }

  public String getQuestion() {
    return question;
  }

  public static DiaryQuestion fromId(int id) {
    return Arrays.stream(values())
        .filter(question -> question.id == id)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid diary question id: " + id));
  }
}
