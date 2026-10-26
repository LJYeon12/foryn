package com.forA.chatbot.diary.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiaryCreateRequest {

  private List<AnswerRequest> answers;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AnswerRequest {

    private Integer questionId;
    private String content;
  }
}

