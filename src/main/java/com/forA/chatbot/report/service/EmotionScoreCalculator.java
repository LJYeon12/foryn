package com.forA.chatbot.report.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.GeneralException;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionScoreCalculator {

  private static final int CHAT_MESSAGE_SESSION_ID_BATCH_SIZE = 500;

  private final MedicationLogRepository medicationLogRepository;
  private final ChatSessionRepository chatSessionRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final DiaryEmotionScoreReader diaryEmotionScoreReader;
  private final ReportMetricsRecorder reportMetricsRecorder;

  public Map<LocalDate, Double> calculateEmotionScoresByDate(
      String reportName, Long userId, LocalDate startDate, LocalDate endDate) {
    return calculateEmotionScoresByDate(reportName, userId, startDate, endDate, false);
  }

  public Map<LocalDate, Double> calculateEmotionScoresByDate(
      String reportName,
      Long userId,
      LocalDate startDate,
      LocalDate endDate,
      boolean includeDiaryScores) {
    Map<LocalDate, List<Integer>> rawScoresByDate = new HashMap<>();

    addChatbotEmotionScoresByDate(reportName, userId, startDate, endDate, rawScoresByDate);
    addMedicationEmotionScoresByDate(reportName, userId, startDate, endDate, rawScoresByDate);
    if (includeDiaryScores) {
      addDiaryEmotionScoresByDate(reportName, userId, startDate, endDate, rawScoresByDate);
    }

    Map<LocalDate, Double> emotionScoresByDate = new HashMap<>();
    reportMetricsRecorder.recordSegment(reportName, "emotion_score_aggregation", () -> {
      rawScoresByDate.forEach((date, scores) -> {
        if (!scores.isEmpty()) {
          int totalScore = scores.stream().mapToInt(Integer::intValue).sum();
          log.debug("Emotion score aggregation: totalScore={}, count={}", totalScore, scores.size());
          emotionScoresByDate.put(date, (double) totalScore / scores.size());
        }
      });
    });

    return emotionScoresByDate;
  }

  private void addDiaryEmotionScoresByDate(
      String reportName,
      Long userId,
      LocalDate startDate,
      LocalDate endDate,
      Map<LocalDate, List<Integer>> rawScoresByDate) {
    try {
      Map<LocalDate, List<Integer>> diaryScoresByDate =
          reportMetricsRecorder.recordSegment(
              reportName,
              "diary_emotion_score_query",
              () -> diaryEmotionScoreReader.findWeightedEmotionScoresByDate(
                  userId, startDate, endDate));

      diaryScoresByDate.forEach((date, scores) -> {
        log.debug("한줄일기 감정 점수 {}건을 집계합니다.", scores.size());
        rawScoresByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).addAll(scores);
      });
    } catch (Exception e) {
      throw reportDataSourceUnavailable(
          reportName,
          "diary_emotion_scores",
          "diary",
          userId,
          startDate,
          endDate,
          e);
    }
  }

  private void addChatbotEmotionScoresByDate(
      String reportName,
      Long userId,
      LocalDate startDate,
      LocalDate endDate,
      Map<LocalDate, List<Integer>> rawScoresByDate) {
    try {
      log.debug("Collecting chatbot emotion scores: {}~{}", startDate, endDate);

      LocalDateTime startOfRange = startDate.atStartOfDay();
      LocalDateTime endOfRange = endDate.plusDays(1).atStartOfDay();
      List<ChatSession> emotionSessions =
          reportMetricsRecorder.recordSegment(
              reportName,
              "chat_session_query",
              () ->
                  chatSessionRepository.findByUserIdAndStartedAtBetween(
                      userId, startOfRange, endOfRange));

      Map<String, LocalDate> sessionDateById =
          emotionSessions.stream()
              .filter(session -> session.getId() != null && !session.getId().isBlank())
              .filter(session -> session.getStartedAt() != null)
              .collect(Collectors.toMap(
                  ChatSession::getId,
                  session -> session.getStartedAt().toLocalDate(),
                  (existing, replacement) -> existing,
                  LinkedHashMap::new));

      if (sessionDateById.isEmpty()) {
        return;
      }

      List<ChatMessage> userMessages =
          reportMetricsRecorder.recordSegment(
              reportName,
              "chat_message_query",
              () ->
                  findUserEmotionMessagesBySessionIds(
                      new ArrayList<>(sessionDateById.keySet())));

      for (ChatMessage message : userMessages) {
        LocalDate sessionDate = sessionDateById.get(message.getSessionId());
        String responseCode = message.getResponseCode();
        if (sessionDate != null && responseCode != null && !responseCode.isEmpty()) {
          List<Integer> emotionScoresFromMessage = parseEmotionCodes(responseCode);
          for (Integer emotionScore : emotionScoresFromMessage) {
            log.debug("Chatbot emotion score: {}", emotionScore);
          }
          rawScoresByDate.computeIfAbsent(sessionDate, ignored -> new ArrayList<>())
              .addAll(emotionScoresFromMessage);
        }
      }
    } catch (Exception e) {
      throw reportDataSourceUnavailable(
          reportName,
          "chatbot_emotion_scores",
          "chatbot",
          userId,
          startDate,
          endDate,
          e);
    }
  }

  private List<ChatMessage> findUserEmotionMessagesBySessionIds(List<String> sessionIds) {
    List<ChatMessage> messages = new ArrayList<>();

    for (int start = 0; start < sessionIds.size(); start += CHAT_MESSAGE_SESSION_ID_BATCH_SIZE) {
      int end = Math.min(start + CHAT_MESSAGE_SESSION_ID_BATCH_SIZE, sessionIds.size());
      List<String> batchSessionIds = new ArrayList<>(sessionIds.subList(start, end));
      messages.addAll(
          chatMessageRepository.findBySessionIdInAndSenderTypeAndChatStep(
              batchSessionIds,
              ChatMessage.SenderType.USER,
              ChatStep.EMOTION_SELECT.name()));
    }

    return messages;
  }

  private void addMedicationEmotionScoresByDate(
      String reportName,
      Long userId,
      LocalDate startDate,
      LocalDate endDate,
      Map<LocalDate, List<Integer>> rawScoresByDate) {
    try {
      List<MedicationLog> medicationLogs =
          reportMetricsRecorder.recordSegment(
              reportName,
              "medication_log_query",
              () ->
                  medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                      userId, startDate, endDate, true));

      Map<LocalDate, List<Integer>> medicationScoresByDate = new HashMap<>();
      for (MedicationLog log : medicationLogs) {
        if (log.getMedCondition() != null) {
          medicationScoresByDate.computeIfAbsent(log.getDate(), ignored -> new ArrayList<>())
              .add(log.getMedCondition());
        }
      }

      medicationScoresByDate.forEach((date, scores) -> {
        List<Integer> limitedScores = scores.size() > 3 ? scores.subList(0, 3) : scores;
        for (Integer emotionScore : limitedScores) {
          log.debug("Medication emotion score: {}", emotionScore);
        }
        rawScoresByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).addAll(limitedScores);
      });
    } catch (Exception e) {
      throw reportDataSourceUnavailable(
          reportName,
          "medication_emotion_scores",
          "medication",
          userId,
          startDate,
          endDate,
          e);
    }
  }

  private List<Integer> parseEmotionCodes(String responseCode) {
    List<Integer> scores = new ArrayList<>();

    String[] emotionCodes = responseCode.split(",");
    for (String code : emotionCodes) {
      try {
        EmotionType emotionType = EmotionType.valueOf(code.trim());
        int score = getEmotionScore(emotionType);
        scores.add(score);
      } catch (IllegalArgumentException e) {
        log.warn("Unknown emotion code: {}", code);
      }
    }

    return scores;
  }

  private int getEmotionScore(EmotionType emotionType) {
    switch (emotionType.getState()) {
      case POSITIVE:
        return 1;
      case NEUTRAL:
        return 0;
      case NEGATIVE:
        return -1;
      default:
        return 0;
    }
  }

  private GeneralException reportDataSourceUnavailable(
      String reportName,
      String segmentName,
      String source,
      Long userId,
      LocalDate startDate,
      LocalDate endDate,
      Exception cause) {
    reportMetricsRecorder.recordSegmentFailure(reportName, segmentName, source);
    log.error(
        "Report data source unavailable - report: {}, segment: {}, source: {}, userId: {}, startDate: {}, endDate: {}",
        reportName,
        segmentName,
        source,
        userId,
        startDate,
        endDate,
        cause);
    return new GeneralException(ErrorStatus.REPORT_DATA_SOURCE_UNAVAILABLE);
  }
}
