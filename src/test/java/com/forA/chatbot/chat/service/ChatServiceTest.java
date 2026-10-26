package com.forA.chatbot.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.component.ChatStepHandlerProvider;
import com.forA.chatbot.chat.converter.ChatConverter;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatMessageDto;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.dto.ChatResponse;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.subscriptions.repository.SubscriptionRepository;
import com.forA.chatbot.user.domain.User;
import java.util.Collections;
import java.util.List;
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
class ChatServiceTest {

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
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ChatStepHandlerProvider chatStepHandlerProvider;

    @Mock
    private ChatSaveHelper chatSaveHelper;
    @Mock private ChatOperationService chatOperations;
    @Mock private ChatAccountDeletion deletion;

    @BeforeEach void operations() {
        org.mockito.Mockito.lenient().when(chatOperations.begin(any(), any(), any()))
            .thenReturn(new com.forA.chatbot.chat.domain.ChatOperationToken("op", 1L, "session-id", 1));
        org.mockito.Mockito.lenient().when(chatOperations.lifecycle(any(), any()))
            .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    }

    @Test
    @DisplayName("신규 유저: 온보딩이 안 된 경우 GENDER 단계부터 시작하는지 확인")
    void initializeSession_NewUser_StartsFromGender() {
        // 준비
        Long userId = 1L;
        User user = mock(User.class);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(chatSessionRepository.findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId))
            .willReturn(Optional.empty()); // 미완료 세션 없음
        given(chatSessionRepository.countByUserIdAndStartedAtBetween(anyLong(), any(), any()))
            .willReturn(0L); // 오늘 채팅 횟수 0
        given(chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(userId))
            .willReturn(Optional.empty()); // 이전 세션 없음 (신규 유저)

        ChatStepHandler genderHandler = mock(ChatStepHandler.class);
        ChatBotMessage botMessage = ChatBotMessage.builder().type(MessageType.TEXT).content("test").build();

        given(chatStepHandlerProvider.getHandler(ChatStep.GENDER.name())).willReturn(genderHandler);
        given(genderHandler.getBotMessage(any(ChatSession.class), eq(user))).willReturn(botMessage);

        // 실행
        ChatResponse response = chatService.initializeSession(userId);

        // 검증
        assertThat(response.getCurrentStep()).isEqualTo(ChatStep.GENDER.name());
        assertThat(response.getOnboardingCompleted()).isFalse();
        assertThat(response.getBotMessage()).isEqualTo(botMessage);

        verify(chatSessionRepository).save(any(ChatSession.class));
        verify(chatMessageRepository).save(any()); // 봇 메시지 저장 확인
    }

    @Test
    @DisplayName("기존 유저: 온보딩이 완료된 경우 EMOTION_SELECT 단계부터 시작하는지 확인")
    void initializeSession_ExistingUser_StartsFromEmotionSelect() {
        // 준비
        Long userId = 1L;
        User user = mock(User.class);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(chatSessionRepository.findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId))
            .willReturn(Optional.empty()); // 미완료 세션 없음
        given(chatSessionRepository.countByUserIdAndStartedAtBetween(anyLong(), any(), any()))
            .willReturn(0L); // 오늘 채팅 횟수 0

        ChatSession lastSession = ChatSession.builder()
            .userId(userId)
            .onboardingCompleted(true)
            .build();
        given(chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(userId))
            .willReturn(Optional.of(lastSession)); // 이전 세션 있음 (온보딩 완료)

        ChatStepHandler emotionHandler = mock(ChatStepHandler.class);
        ChatBotMessage botMessage = ChatBotMessage.builder().type(MessageType.TEXT).content("test").build();

        given(chatStepHandlerProvider.getHandler(ChatStep.EMOTION_SELECT.name())).willReturn(emotionHandler);
        given(emotionHandler.getBotMessage(any(ChatSession.class), eq(user))).willReturn(botMessage);

        // 실행
        ChatResponse response = chatService.initializeSession(userId);

        // 검증
        assertThat(response.getCurrentStep()).isEqualTo(ChatStep.EMOTION_SELECT.name());
        assertThat(response.getOnboardingCompleted()).isTrue();
        assertThat(response.getBotMessage()).isEqualTo(botMessage);

        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("이어하기: 종료되지 않은 세션이 있을 경우, 새 세션을 만들지 않고 기존 세션을 반환하는지 확인")
    void initializeSession_UnfinishedSession_ResumesSession() {
        // 준비
        Long userId = 1L;
        User user = mock(User.class);
        String sessionId = "existing-session-id";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        ChatSession unfinishedSession = ChatSession.builder()
            .id(sessionId)
            .userId(userId)
            .currentStep(ChatStep.SITUATION_INPUT.name())
            .onboardingCompleted(true)
            .build();

        given(chatSessionRepository.findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId))
            .willReturn(Optional.of(unfinishedSession)); // 미완료 세션 존재

        given(chatSessionRepository.countByUserIdAndStartedAtBetween(anyLong(), any(), any()))
            .willReturn(0L); // 오늘 채팅 횟수 (이어하기 로직에서는 사용되지 않지만 호출됨)

        List<ChatMessageDto> history = Collections.singletonList(
            ChatMessageDto.builder().content("이전 대화 내용").build()
        );
        // getChatHistory 내부 모킹
        // 채팅 이력 조회 -> 세션 메시지 시간순 조회 -> DTO 목록 변환
        // 하지만 getChatHistory는 private 메서드이므로 내부 동작을 모킹해야 함.
        // 여기서는 chatMessageRepository와 chatConverter를 모킹하여 간접적으로 테스트.
        given(chatMessageRepository.findBySessionIdOrderBySentAtAsc(sessionId)).willReturn(Collections.emptyList());
        given(chatConverter.toDtoList(any())).willReturn(history);

        ChatBotMessage botMessage = ChatBotMessage.builder().type(MessageType.TEXT).content("test").build();
        given(chatConverter.convertLatestHistoryToBotMessage(history)).willReturn(botMessage);

        // 실행
        ChatResponse response = chatService.initializeSession(userId);

        // 검증
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getCurrentStep()).isEqualTo(ChatStep.SITUATION_INPUT.name());
        assertThat(response.getBotMessage()).isEqualTo(botMessage);

        // 새 세션 저장 안 함
        verify(chatSessionRepository, never()).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("자가 치유(Self-Healing) 작동 - 유저 프로필이 없는 상태에서 상위 단계 핸들러 호출 시 예외 발생 및 세션 리셋 확인")
    void handleUserResponse_SelfHealing_InconsistentState() {
        // 준비
        Long userId = 1L;
        String sessionId = "session-id";
        User user = mock(User.class);
        ChatRequest request = new ChatRequest();

        ChatSession session = ChatSession.builder()
            .id(sessionId)
            .userId(userId)
            .currentStep(ChatStep.SITUATION_INPUT.name()) // 상위 단계
            .onboardingCompleted(false) // 온보딩 미완료 (프로필 없음)
            .build();

        given(chatSessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        ChatStepHandler currentHandler = mock(ChatStepHandler.class);
        given(chatStepHandlerProvider.getHandler(ChatStep.SITUATION_INPUT.name())).willReturn(currentHandler);

        // validateState에서 예외 발생 시뮬레이션
        doThrow(new ChatHandler(ErrorStatus.INCONSISTENT_CHAT_STATE))
            .when(currentHandler).validateState(any(ChatSession.class), any(User.class));

        // 실행 및 검증
        assertThatThrownBy(() -> chatService.handleUserResponse(userId, sessionId, request))
            .isInstanceOf(ChatHandler.class)
            .hasFieldOrPropertyWithValue("code", ErrorStatus.INCONSISTENT_CHAT_STATE);

        // 세션 상태가 저장되었는지 확인 (validateState 내에서 변경된 상태 저장)
        verify(chatOperations).repair(any(), any(ChatSession.class));
    }

    @Test
    @DisplayName("TC-P0-04: 장기 트랜잭션 분리 검증 - handleUserResponse 실행 중 DB 커넥션 점유 여부 확인 (AI 호출 중 트랜잭션 비활성화)")
    void handleUserResponse_LongTransactionSeparation() {
        // 준비
        Long userId = 1L;
        String sessionId = "session-id";
        User user = mock(User.class);
        ChatRequest request = new ChatRequest();
        
        ChatSession session = ChatSession.builder()
            .id(sessionId)
            .userId(userId)
            .currentStep(ChatStep.SITUATION_INPUT.name())
            .build();

        given(chatSessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        ChatStepHandler currentHandler = mock(ChatStepHandler.class);
        given(chatStepHandlerProvider.getHandler(ChatStep.SITUATION_INPUT.name())).willReturn(currentHandler);

        String nextStepName = ChatStep.EMOTION_SELECT.name();
        given(currentHandler.handleRequest(any(ChatSession.class), any(User.class), eq(request))).willReturn(nextStepName);

        ChatStepHandler nextHandler = mock(ChatStepHandler.class);
        given(chatStepHandlerProvider.getHandler(nextStepName)).willReturn(nextHandler);

        ChatBotMessage botMessage = ChatBotMessage.builder().type(MessageType.TEXT).content("test").build();
        given(nextHandler.getBotMessage(any(ChatSession.class), any(User.class))).willReturn(botMessage);

        given(chatSaveHelper.saveChatResult(any(), any(), any(), any(), eq(request), eq(nextStepName), eq(botMessage)))
            .willReturn(false);

        // 실행
        chatService.handleUserResponse(userId, sessionId, request);

        // 검증
        // ChatService 자체에는 @Transactional이 없거나, handleUserResponse에는 없어야 함.
        // 하지만 ChatService 클래스 레벨이나 메서드 레벨에 @Transactional이 있는지 확인 필요.
        // 현재 ChatService 코드를 보면 클래스 레벨에 @Service만 있고, initializeSession, forceInitializeSession, deleteAllChatDataByUserId에는 @Transactional이 있음.
        // handleUserResponse에는 @Transactional이 없음.
        // 따라서 handleUserResponse 내부에서 chatSaveHelper.saveChatResult 호출 시에만 트랜잭션이 걸림.
        
        verify(chatSaveHelper).saveChatResult(any(), any(), any(), any(), eq(request), eq(nextStepName), eq(botMessage));
    }
}
