package com.forA.chatbot.chat.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.apiPayload.exception.handler.SubscriptionHandler;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.component.ChatStepHandlerProvider;
import com.forA.chatbot.chat.converter.ChatConverter;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatMessageDto;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.dto.ChatResponse;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.subscriptions.domain.Subscription;
import com.forA.chatbot.subscriptions.repository.SubscriptionRepository;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

  private final ChatSessionRepository chatSessionRepository; // 세션 관리
  private final ChatMessageRepository chatMessageRepository; // 메시지 기록
  private final UserRepository userRepository;
  private final ChatConverter chatConverter;
  private final SubscriptionRepository subscriptionRepository;
  private final ChatStepHandlerProvider chatStepHandlerProvider;
  private final ChatSaveHelper chatSaveHelper;
  private final ChatOperationService chatOperations;
  private final ChatAccountDeletion chatAccountDeletion;

  @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
  public ChatResponse initializeSession(Long userId) {
    ChatOperationToken token = chatOperations.begin(userId, null, "INITIALIZE");
    return chatOperations.lifecycle(token, () -> initializeUnderGuard(userId));
  }

  private ChatResponse initializeUnderGuard(Long userId) {
    // TODO : 구독 여부 체크 주석 해제
    // checkPremiumStatus(userId);

    log.info("채팅 세션 초기화/재개: {}", userId);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

    ChatSession session;
    ChatBotMessage botMessage;
    List<ChatMessageDto> history = new ArrayList<>();
    int todayChatCount = getTodayChatCount(userId);

    Optional<ChatSession> unfinishedSessionOpt = chatSessionRepository
        .findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId);

    if (unfinishedSessionOpt.isPresent()) { // 세션 미완
      session = unfinishedSessionOpt.get();
      history = getChatHistory(session.getId());
      botMessage = chatConverter.convertLatestHistoryToBotMessage(history);
      log.info("미완료 세션 재개: {}", session.getId());
    } else { // 세션 완
      Optional<ChatSession> lastSessionOpt = chatSessionRepository.findFirstByUserIdOrderByStartedAtDesc(userId); // 가장                                                                                                          // 가져오기
      String initialStep = lastSessionOpt
          .filter(s -> Boolean.TRUE.equals(s.getOnboardingCompleted()))
          .map(s -> ChatStep.EMOTION_SELECT.name())
          .orElseGet(() -> determineStartingStep(user));
      boolean isUserOnboarded = ChatStep.EMOTION_SELECT.name().equals(initialStep);
      session = ChatSession.builder()
          .userId(userId)
          .currentStep(initialStep)
          .onboardingCompleted(isUserOnboarded)
          .startedAt(LocalDateTime.now())
          .build();

      chatSessionRepository.save(session);

      todayChatCount++;
      log.info("새 세션 시작: {}, 초기 단계: {}", isUserOnboarded, initialStep);

      // 핸들러를 통해 봇 메시지 생성
      ChatStepHandler handler = chatStepHandlerProvider.getHandler(session.getCurrentStep());
      botMessage = handler.getBotMessage(session, user);

      recordBotMessage(session.getId(), session.getCurrentStep(), botMessage);
      history.add(chatConverter.toBotChatMessageDto(botMessage, session));
    }
    return chatConverter.toChatResponse(session, history, botMessage, false, todayChatCount);
  }

  private int getTodayChatCount(Long userId) {
    LocalDateTime startOfDay = LocalDate.now().atStartOfDay(); // 00:00:00
    LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX); // 23:59:59.999...

    // 오늘 시작된 세션 수를 계산
    long count = chatSessionRepository.countByUserIdAndStartedAtBetween(userId, startOfDay, endOfDay);
    return (int) count;
  }
  @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
  public ChatResponse handleUserResponse(Long userId, String sessionId, ChatRequest request) {
    // checkPremiumStatus(userId);

    ChatSession session = chatSessionRepository.findById(sessionId)
        .orElseThrow(() -> new ChatHandler(ErrorStatus.SESSION_NOT_FOUND));
    requireOwner(session, userId);
    ChatOperationToken token = chatOperations.begin(userId, sessionId, "TURN");
    boolean saving = false;
    try {
      // 작업권 획득 후 다시 읽는다. 최초 소유권 조회가 이전 대화 차례의 커밋보다 앞설 수 있다.
      session = chatSessionRepository.findById(sessionId)
          .orElseThrow(() -> new ChatHandler(ErrorStatus.SESSION_NOT_FOUND)).workingCopy();
      requireOwner(session, userId);
      if (session.getEndedAt() != null) throw new ChatHandler(ErrorStatus.CHAT_STATE_CONFLICT);
      User stored = userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
      if (stored.isDeleted()) throw new UserHandler(ErrorStatus.USER_NOT_FOUND);
      User before = UserProfileDelta.workingCopy(stored);
      User working = UserProfileDelta.workingCopy(before);
      ChatStepHandler currentHandler = chatStepHandlerProvider.getHandler(session.getCurrentStep());
      try {
        currentHandler.validateState(session, working);
      } catch (ChatHandler invalidState) {
        chatOperations.repair(token, session);
        throw invalidState;
      }
      String nextStepName = currentHandler.handleRequest(session, working, request);
      ChatBotMessage botMessage = chatStepHandlerProvider.getHandler(nextStepName).getBotMessage(session, working);
      int todayChatCount = getTodayChatCount(userId);
      saving = true;
      boolean isChatEnd = chatSaveHelper.saveChatResult(token, session, before, working, request, nextStepName, botMessage);
      return chatConverter.toChatResponse(session, nextStepName, botMessage, isChatEnd, todayChatCount);
    } catch (RuntimeException failure) {
      if (!saving) chatOperations.cancelPreparation(token);
      throw failure;
    }
  }

  private void requireOwner(ChatSession session, Long userId) {
    if (!userId.equals(session.getUserId())) throw new ChatHandler(ErrorStatus.SESSION_NOT_FOUND);
  }

  @Transactional(readOnly = true)
  public List<ChatMessageDto> getRecentChatHistory(Long userId) {
    LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);

    // 최근에 종료된 세션 조회 (ArrayList로 감싸서 수정 가능하게 변경)
    List<ChatSession> recentSessions = new ArrayList<>(chatSessionRepository.findByUserIdAndEndedAtAfter(
        userId, cutoffTime));

    // 현재 진행 중인(미완료) 세션도 포함
    chatSessionRepository.findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId)
        .ifPresent(recentSessions::add);

    if (recentSessions.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> sessionIds = recentSessions.stream()
        .map(ChatSession::getId)
        .collect(Collectors.toList());
    // 해당 세션들의 모든 메시지 조회
    List<ChatMessage> messages = chatMessageRepository.findBySessionIdInOrderBySentAtAsc(
        sessionIds);
    return chatConverter.toDtoList(messages);
  }

  private List<ChatMessageDto> getChatHistory(String sessionId) {
    List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderBySentAtAsc(sessionId);
    return chatConverter.toDtoList(messages);
  }

  private void recordBotMessage(String sessionId, String step, ChatBotMessage botMessage) {
    chatMessageRepository.save(chatConverter.toBotMessageEntity(sessionId, step, botMessage));
  }

  /*
   * 기존에 미완료된 세션이 있다면 강제로 종료시키고, initializeSession을 호출하여 새 세션을 시작
   */
  @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
  public ChatResponse forceInitializeSession(Long userId) {
    ChatOperationToken token = chatOperations.begin(userId, null, "INITIALIZE");
    return chatOperations.lifecycle(token, () -> forceInitializeUnderGuard(userId));
  }

  private ChatResponse forceInitializeUnderGuard(Long userId) {
    log.info("채팅 세션 강제 새로 시작: {}", userId);

    // 기존 미완료 세션이 있다면 종료시킴
    Optional<ChatSession> unfinishedSessionOpt = chatSessionRepository
        .findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(userId);

    if (unfinishedSessionOpt.isPresent()) {
      ChatSession unfinishedSession = unfinishedSessionOpt.get();
      unfinishedSession.setEndedAt(LocalDateTime.now());
      unfinishedSession.clearTemporaryData();
      chatSessionRepository.save(unfinishedSession);
      log.info("기존 미완료 세션 강제 종료: {}", unfinishedSession.getId());
    }

    return initializeUnderGuard(userId);
  }

  private void checkPremiumStatus(Long userId) {
    Subscription subscription = subscriptionRepository.findByUser_Id(userId)
        .orElseThrow(() -> new SubscriptionHandler(ErrorStatus.IAP_SUBSCRIPTION_NOT_FOUND));
    if (!subscription.isActive()) {
      throw new SubscriptionHandler(ErrorStatus.IAP_PREMIUM_REQUIRED);
    }
  }

  public void deleteAllChatDataByUserId(Long userId) {
    chatAccountDeletion.delete(userId);
  }

  private String determineStartingStep(User user) {
    if (user.getGender() == null || user.getGender() == Gender.UNKNOWN)
      return ChatStep.GENDER.name();
    if (user.getBirthYear() == null)
      return ChatStep.BIRTH_YEAR.name();
    if (user.getJobs().isEmpty())
      return ChatStep.JOB_TYPE.name();
    if (user.getDisorders().isEmpty())
      return ChatStep.DISORDER_TYPE.name();
    if (!user.getDisorders().contains(DisorderType.NONE) && user.getSymptoms().isEmpty())
      return ChatStep.SYMPTOM_TYPE.name();
    return ChatStep.EMOTION_SELECT.name();
  }
}
