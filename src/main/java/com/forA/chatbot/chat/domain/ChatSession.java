package com.forA.chatbot.chat.domain;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@Builder
@Document(collection = "chat_sessions")
public class ChatSession {

  @Id private String id; // MongoDB의 ObjectId

  @Field(name = "user_id")
  private Long userId; // JPA User 엔티티와의 연결 고리

  // 유저 기본 데이터 수집
  @Field(name = "current_step")
  private String currentStep;

  // 데이터 수집 완료 여부.
  @Field(name = "onboarding_completed")
  @Builder.Default
  private Boolean onboardingCompleted = false;

  @Field("temporary_data")
  @Builder.Default
  private Map<String, String> temporaryData = new HashMap<>();

  // 채팅 세션의 시작 시간
  @Field(name = "started_at")
  private LocalDateTime startedAt;

  // 채팅 세션의 종료 시간 (비정상 종료 포함)
  @Field(name = "ended_at")
  private LocalDateTime endedAt;

  // 가장 마지막으로 채팅 기록이 저장된 시간 (재접속 시점 추론에 사용)
  @Field(name = "last_interaction_at")
  private LocalDateTime lastInteractionAt;

  @Field("state_version")
  @Builder.Default
  private Long stateVersion = 0L;

  // 기존 문서에는 버전이 없다. 래퍼 타입 생성자 인자는 null을 허용해야 한다.
  public long getStateVersion() { return stateVersion == null ? 0L : stateVersion; }

  @Field("pending_turn")
  private PendingChatTurn pendingTurn;

  @Field("last_committed_operation_id")
  private String lastCommittedOperationId;

  public ChatSession workingCopy() {
    return ChatSession.builder().id(id).userId(userId).currentStep(currentStep)
        .onboardingCompleted(onboardingCompleted).temporaryData(temporaryData == null
            ? new HashMap<>() : new HashMap<>(temporaryData))
        .startedAt(startedAt).endedAt(endedAt).lastInteractionAt(lastInteractionAt)
        .stateVersion(stateVersion).lastCommittedOperationId(lastCommittedOperationId).build();
  }

  public void setTemporaryData(String key, String value) {
    if (this.temporaryData == null) {
      this.temporaryData = new HashMap<>();
    }
    this.temporaryData.put(key, value);
  }

  public String getTemporaryData(String key) {
    return (this.temporaryData != null) ? this.temporaryData.get(key) : null;
  }

  public void clearTemporaryData() {
    if (this.temporaryData != null) {
      this.temporaryData.clear();
    }
  }
}
