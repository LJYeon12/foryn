package com.forA.chatbot.chat.component.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.GeneralException;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SymptomStepHandlerTest {

  private SymptomStepHandler handler;
  private ChatSession session;
  private User user;

  @BeforeEach
  void setUp() {
    handler = new SymptomStepHandler();
    session = ChatSession.builder().currentStep("SYMPTOM_TYPE").build();
    user =
        User.builder()
            .email("test@test.com")
            .providerUserId("provider-1")
            .providerType(ProviderType.APPLE)
            .fullName("Test User")
            .disorders(Set.of(DisorderType.DEPRESSION))
            .build();
  }

  @Test
  @DisplayName("description에 쉼표가 포함된 증상을 단일 선택하면 정상 매칭된다")
  void matchesSingleSymptomWithCommaInDescription() {
    ChatRequest request = new ChatRequest();
    request.setResponseValue(SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription());

    String nextStep = handler.handleRequest(session, user, request);

    assertThat(nextStep).isEqualTo("EMOTION_SELECT");
    assertThat(user.getSymptoms()).containsExactly(SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES);
  }

  @Test
  @DisplayName("쉼표 포함 증상과 일반 증상을 함께 선택하면 모두 정상 매칭된다")
  void matchesMultipleSymptomsIncludingCommaDescription() {
    String commaDescription = SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription();
    String plainDescription = SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription();

    ChatRequest request = new ChatRequest();
    request.setResponseValue(commaDescription + "," + plainDescription);

    String nextStep = handler.handleRequest(session, user, request);

    assertThat(nextStep).isEqualTo("EMOTION_SELECT");
    assertThat(user.getSymptoms())
        .containsExactlyInAnyOrder(
            SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES,
            SymptomType.LETHARGY_AND_UNWILLINGNESS);
  }

  @Test
  @DisplayName("매칭되는 증상이 없으면 INVALID_BUTTON_SELECTION 예외가 발생한다")
  void throwsWhenNoSymptomsMatch() {
    ChatRequest request = new ChatRequest();
    request.setResponseValue("존재하지 않는 증상입니다");

    assertThatThrownBy(() -> handler.handleRequest(session, user, request))
        .isInstanceOf(ChatHandler.class);
  }

  @Nested
  @DisplayName("질환별 증상 선택 한도 검증")
  class DisorderSymptomLimitTest {

    @Test
    @DisplayName("질환 1개에서 증상 3개 선택은 허용된다")
    void allowsThreeSymptomsForSingleDisorder() {
      ChatRequest request = new ChatRequest();
      request.setResponseValue(
          SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription()
              + "|"
              + SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription()
              + "|"
              + SymptomType.FEELING_WORTHLESS.getDescription());

      String nextStep = handler.handleRequest(session, user, request);

      assertThat(nextStep).isEqualTo("EMOTION_SELECT");
      assertThat(user.getSymptoms()).hasSize(3);
    }

    @Test
    @DisplayName("질환 2개에서 각 3개씩 총 6개 선택은 허용된다")
    void allowsSixSymptomsAcrossTwoDisorders() {
      User multiDisorderUser =
          User.builder()
              .email("multi@test.com")
              .providerUserId("provider-2")
              .providerType(ProviderType.APPLE)
              .fullName("Multi Disorder User")
              .disorders(Set.of(DisorderType.DEPRESSION, DisorderType.ANXIETY_DISORDER))
              .build();

      ChatRequest request = new ChatRequest();
      request.setResponseValue(
          SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription()
              + "|"
              + SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription()
              + "|"
              + SymptomType.FEELING_WORTHLESS.getDescription()
              + "|"
              + SymptomType.CONSTANT_FUTURE_WORRIES.getDescription()
              + "|"
              + SymptomType.FEAR_OF_MAKING_MISTAKES.getDescription()
              + "|"
              + SymptomType.CHEST_TIGHTNESS_AND_BREATHLESSNESS.getDescription());

      String nextStep = handler.handleRequest(session, multiDisorderUser, request);

      assertThat(nextStep).isEqualTo("EMOTION_SELECT");
      assertThat(multiDisorderUser.getSymptoms()).hasSize(6);
    }

    @Test
    @DisplayName("질환 1개에서 증상 4개 선택 시 CHAT_SELECTION_LIMIT_EXCEEDED가 발생한다")
    void rejectsFourSymptomsForSingleDisorder() {
      ChatRequest request = new ChatRequest();
      request.setResponseValue(
          SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription()
              + "|"
              + SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription()
              + "|"
              + SymptomType.FEELING_WORTHLESS.getDescription()
              + "|"
              + SymptomType.DIFFICULTY_CONCENTRATING.getDescription());

      assertThatThrownBy(() -> handler.handleRequest(session, user, request))
          .isInstanceOf(ChatHandler.class)
          .satisfies(
              ex ->
                  assertThat(((GeneralException) ex).getErrorReasonHttpStatus().getCode())
                      .isEqualTo(ErrorStatus.CHAT_SELECTION_LIMIT_EXCEEDED.getCode()));
    }

    @Test
    @DisplayName("질환 2개 중 한 쪽에서만 4개 선택해도 CHAT_SELECTION_LIMIT_EXCEEDED가 발생한다")
    void rejectsFourSymptomsForOneDisorderAmongMultipleDisorders() {
      User multiDisorderUser =
          User.builder()
              .email("multi2@test.com")
              .providerUserId("provider-3")
              .providerType(ProviderType.APPLE)
              .fullName("Multi Disorder User 2")
              .disorders(Set.of(DisorderType.DEPRESSION, DisorderType.ANXIETY_DISORDER))
              .build();

      ChatRequest request = new ChatRequest();
      request.setResponseValue(
          SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription()
              + "|"
              + SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription()
              + "|"
              + SymptomType.FEELING_WORTHLESS.getDescription()
              + "|"
              + SymptomType.DIFFICULTY_CONCENTRATING.getDescription()
              + "|"
              + SymptomType.CONSTANT_FUTURE_WORRIES.getDescription());

      assertThatThrownBy(() -> handler.handleRequest(session, multiDisorderUser, request))
          .isInstanceOf(ChatHandler.class)
          .satisfies(
              ex ->
                  assertThat(((GeneralException) ex).getErrorReasonHttpStatus().getCode())
                      .isEqualTo(ErrorStatus.CHAT_SELECTION_LIMIT_EXCEEDED.getCode()));
    }
  }
}
