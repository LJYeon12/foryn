package com.forA.chatbot.report.fault;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.report.converter.ReportConverter;
import com.forA.chatbot.report.dto.ReportResponseDto;
import com.forA.chatbot.report.service.ReportService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 과거 운영 리비전을 체크아웃한 결과가 아니라 재구성한 비교다. */
final class ReportFaultWorkload {
  enum Variant {
    A_REPEAT_TX(true, false), B_REPEAT_NO_TX(false, false),
    C_BATCH_TX(true, true), D_BATCH_NO_TX(false, true);

    final boolean transaction;
    final boolean batched;
    Variant(boolean transaction, boolean batched) {
      this.transaction = transaction;
      this.batched = batched;
    }
  }

  private final ReportService current;
  private final UserRepository users;
  private final ChatSessionRepository sessions;
  private final ChatMessageRepository messages;
  private final MedicationLogRepository medications;
  private final ReportConverter converter;
  private final TransactionTemplate transaction;

  ReportFaultWorkload(ReportService current, UserRepository users, ChatSessionRepository sessions,
      ChatMessageRepository messages, MedicationLogRepository medications, ReportConverter converter,
      PlatformTransactionManager manager) {
    this.current = current;
    this.users = users;
    this.sessions = sessions;
    this.messages = messages;
    this.medications = medications;
    this.converter = converter;
    this.transaction = new TransactionTemplate(manager);
    this.transaction.setReadOnly(true);
  }

  boolean mysqlUserExists(long userId) {
    return users.findById(userId).isPresent();
  }

  ReportResponseDto.WeeklyEmotionReportResponse report(Variant variant, long userId) {
    Supplier<ReportResponseDto.WeeklyEmotionReportResponse> calculation = variant.batched
        ? () -> current.getWeeklyEmotionReport(userId) : () -> repeated(userId);
    return variant.transaction ? transaction.execute(status -> calculation.get()) : calculation.get();
  }

  private ReportResponseDto.WeeklyEmotionReportResponse repeated(long userId) {
    LocalDate end = LocalDate.now();
    LocalDate start = end.minusDays(13);
    Map<LocalDate, Double> daily = new HashMap<>();
    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
      List<Integer> scores = new ArrayList<>();
      List<ChatSession> found = sessions.findByUserIdAndStartedAtBetween(
          userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
      for (ChatSession session : found) {
        for (ChatMessage message : messages.findBySessionIdAndSenderTypeAndChatStep(
            session.getId(), ChatMessage.SenderType.USER, ChatStep.EMOTION_SELECT.name())) {
          if (message.getResponseCode() != null) {
            for (String code : message.getResponseCode().split(",")) {
              try {
                scores.add(switch (EmotionType.valueOf(code.trim()).getState()) {
                  case POSITIVE -> 1;
                  case NEUTRAL -> 0;
                  case NEGATIVE -> -1;
                });
              } catch (IllegalArgumentException ignored) {
                // 알 수 없는 코드는 현재 운영 계산기와 같은 정책으로 처리한다.
              }
            }
          }
        }
      }
      medications.findByUserIdAndDateAndIsTaken(userId, date, true).stream()
          .map(MedicationLog::getMedCondition).filter(java.util.Objects::nonNull)
          .limit(3).forEach(scores::add);
      daily.put(date, scores.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
    return converter.toWeeklyEmotionReport(days(daily, end.minusDays(6)), days(daily, start));
  }

  private List<ReportResponseDto.DailyEmotionReportDto> days(Map<LocalDate, Double> scores, LocalDate start) {
    return IntStream.range(0, 7).mapToObj(start::plusDays)
        .map(date -> converter.toDailyEmotionReport(date,
            date.getDayOfWeek().toString(), scores.getOrDefault(date, 0.0))).toList();
  }
}
