package com.forA.chatbot.chat.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatResponse {
  private String sessionId;
  private String currentStep; // 현재 대화 단계
  private List<ChatMessageDto> messages; // 현재까지의 대화 기록 (세션 재개 시 유용)
  private ChatBotMessage botMessage; // 챗봇의 다음 메시지
  private Boolean isCompleted; // 현재 단계가 완료되었는지 여부
  private Boolean onboardingCompleted;
  private Integer todayChatCount;
}
