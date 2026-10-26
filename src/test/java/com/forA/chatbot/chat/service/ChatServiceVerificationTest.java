package com.forA.chatbot.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.component.ChatStepHandlerProvider;
import com.forA.chatbot.chat.component.impl.ActionFeedbackStepHandler;
import com.forA.chatbot.chat.component.impl.ChatEndStepHandler;
import com.forA.chatbot.chat.component.impl.EmotionStepHandler;
import com.forA.chatbot.chat.converter.ChatConverter;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.dto.ChatResponse;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceVerificationTest {

    @InjectMocks
    private ChatService chatService;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ChatConverter chatConverter = new ChatConverter(new ObjectMapper());

    @Mock
    private ChatStepHandlerProvider chatStepHandlerProvider;
    @Mock
    private ChatSaveHelper chatSaveHelper;
    @Mock private ChatOperationService chatOperations;
    @Mock private ChatAccountDeletion deletion;

    @BeforeEach void operations() {
        org.mockito.Mockito.lenient().when(chatOperations.begin(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new com.forA.chatbot.chat.domain.ChatOperationToken("op", 1L, "session-id", 1));
        org.mockito.Mockito.lenient().when(chatOperations.lifecycle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    }
    @Test
    @DisplayName("EmotionSTEP: 이모지 응답이 정상적으로 오는지 확인")
    void initializeSession_EmotionStep_ReturnsEmojiOptions() {
        // 준비
        Long userId = 1L;
        User user = mock(User.class);
        given(user.getNickname()).willReturn("테스트유저");

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(chatSessionRepository.findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId))
            .willReturn(Optional.empty());

        ChatSession lastSession = ChatSession.builder()
            .userId(userId)
            .onboardingCompleted(true)
            .build();
        given(chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(userId))
            .willReturn(Optional.of(lastSession));

        // 실제 핸들러 사용
        ChatStepHandler emotionHandler = new EmotionStepHandler();
        given(chatStepHandlerProvider.getHandler(ChatStep.EMOTION_SELECT.name())).willReturn(emotionHandler);

        // 실행
        ChatResponse response = chatService.initializeSession(userId);

        // 검증
        assertThat(response.getCurrentStep()).isEqualTo(ChatStep.EMOTION_SELECT.name());
        assertThat(response.getBotMessage().getType()).isEqualTo(MessageType.OPTION);
        assertThat(response.getBotMessage().getOptions()).isNotEmpty();
        assertThat(response.getBotMessage().getOptions().get(0).getEmoji()).isNotNull();
    }

    @Test
    @DisplayName("ActionFeedback STEP: request 요청시 의도된 응답이 오는지 확인")
    void handleUserResponse_ActionFeedbackStep_ReturnsEndMessage() {
        // 준비
        Long userId = 1L;
        String sessionId = "session-id";
        User user = mock(User.class);
        given(user.getNickname()).willReturn("테스트유저");

        ChatSession session = ChatSession.builder()
            .id(sessionId)
            .userId(userId)
            .currentStep(ChatStep.ACTION_FEEDBACK.name())
            .onboardingCompleted(true)
            .build();
        
        // 실제 시나리오: 이전 단계에서 감정을 선택한 상태여야 함
        session.setTemporaryData("selectedEmotions", EmotionType.SADNESS.name());

        given(chatSessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        ChatRequest request = new ChatRequest();
        request.setResponseValue("조금 나아졌어요"); // 실제 사용자가 입력할 법한 피드백 텍스트

        // 실제 핸들러 사용
        ChatStepHandler actionFeedbackHandler = new ActionFeedbackStepHandler();
        ChatStepHandler chatEndHandler = new ChatEndStepHandler(chatSessionRepository);

        given(chatStepHandlerProvider.getHandler(ChatStep.ACTION_FEEDBACK.name())).willReturn(actionFeedbackHandler);
        given(chatStepHandlerProvider.getHandler(ChatStep.CHAT_END.name())).willReturn(chatEndHandler);

        // 실행
        ChatResponse response = chatService.handleUserResponse(userId, sessionId, request);

        // 검증
        assertThat(response.getCurrentStep()).isEqualTo(ChatStep.CHAT_END.name());
        // ActionFeedback을 거쳐서 종료된 경우의 특정 메시지 검증
        assertThat(response.getBotMessage().getContent()).contains("힘들 때마다 언제든 포린을 찾아주세요");
        assertThat(response.getBotMessage().getContent()).doesNotContain("null 다시 시도해주세요");
    }
}
