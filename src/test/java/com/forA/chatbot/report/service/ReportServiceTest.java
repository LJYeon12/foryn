package com.forA.chatbot.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.GeneralException;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.diary.domain.DiaryReportSummaryCache;
import com.forA.chatbot.diary.repository.DiaryReportSummaryCacheRepository;
import com.forA.chatbot.diary.service.DiaryAiService;
import com.forA.chatbot.diary.service.DiaryTagSummaryReader;
import com.forA.chatbot.diary.service.RecentDiaryTagSummary;
import com.forA.chatbot.global.time.AppDateTimeProvider;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.report.converter.ReportConverter;
import com.forA.chatbot.report.dto.ReportResponseDto;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private MedicationBundleRepository medicationBundleRepository;

  @Mock
  private MedicationLogRepository medicationLogRepository;

  @Mock
  private ChatSessionRepository chatSessionRepository;

  @Mock
  private ChatMessageRepository chatMessageRepository;

  @Mock
  private DiaryEmotionScoreReader diaryEmotionScoreReader;

  @Mock
  private DiaryTagSummaryReader diaryTagSummaryReader;

  @Mock
  private DiaryReportSummaryCacheRepository summaryCacheRepository;

  @Mock
  private DiaryAiService diaryAiService;

  @Mock
  private AppDateTimeProvider appDateTimeProvider;

  @Spy
  private ReportConverter reportConverter = new ReportConverter();

  @Spy
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private ReportMetricsRecorder reportMetricsRecorder;

  private EmotionScoreCalculator emotionScoreCalculator;

  private ReportService reportService;

  @BeforeEach
  void setUp() {
    reportMetricsRecorder = new ReportMetricsRecorder(meterRegistry);
    lenient().when(appDateTimeProvider.today(anyString()))
        .thenAnswer(invocation -> LocalDate.now(ZoneId.of(invocation.getArgument(0))));
    lenient().when(appDateTimeProvider.currentMonth(anyString()))
        .thenAnswer(invocation -> YearMonth.now(ZoneId.of(invocation.getArgument(0))));
    emotionScoreCalculator =
        new EmotionScoreCalculator(
            medicationLogRepository,
            chatSessionRepository,
            chatMessageRepository,
            diaryEmotionScoreReader,
            reportMetricsRecorder);
    reportService =
        new ReportService(
            userRepository,
            medicationBundleRepository,
            medicationLogRepository,
            reportConverter,
            reportMetricsRecorder,
            emotionScoreCalculator,
            diaryTagSummaryReader,
            summaryCacheRepository,
            diaryAiService,
            appDateTimeProvider);
  }

  @Test
  @DisplayName("ReportService should not hold a service-wide transaction")
  void reportService_hasNoServiceWideTransaction() {
    assertThat(ReportService.class.getAnnotation(Transactional.class)).isNull();
  }

  @Test
  @DisplayName("getWeeklyMedicationReport preserves medication status and formatted time")
  void getWeeklyMedicationReport_preservesMedicationStatus() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    User user = User.builder().id(userId).email("user@example.com").build();
    ReflectionTestUtils.setField(user, "createdAt", today.minusDays(6).atStartOfDay());

    MedicationBundle bundle =
        MedicationBundle.builder()
            .user(user)
            .bundleName("vitamin")
            .dayOfWeek(today.getDayOfWeek().name())
            .scheduledTime(LocalTime.of(9, 0))
            .build();
    ReflectionTestUtils.setField(bundle, "id", 10L);
    ReflectionTestUtils.setField(bundle, "createdAt", today.minusDays(6).atStartOfDay());

    MedicationLog log =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(today)
            .isTaken(true)
            .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findAllByUserId(userId)).willReturn(List.of(bundle));
    given(
            medicationLogRepository.findByMedicationBundle_UserAndDateBetween(
                eq(user), eq(today.minusDays(6)), eq(today)))
        .willReturn(List.of(log));

    ReportResponseDto.WeeklyReportResponse result =
        reportService.getWeeklyMedicationReport(userId);

    assertThat(result.getWeekData()).hasSize(7);
    ReportResponseDto.DailyMedicationReportDto todayReport =
        result.getWeekData().get(result.getWeekData().size() - 1);
    assertThat(todayReport.getDate()).isEqualTo(today.toString());
    assertThat(todayReport.getMedications()).hasSize(1);
    assertThat(todayReport.getMedications().get(0).getBundleName()).isEqualTo("vitamin");
    assertThat(todayReport.getMedications().get(0).getScheduledTime()).isEqualTo("09:00");
    assertThat(todayReport.getMedications().get(0).isTaken()).isTrue();
  }

  @Test
  @DisplayName("getWeeklyMedicationReport skips malformed day_of_week bundles")
  void getWeeklyMedicationReport_skipsMalformedDayOfWeekBundles() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    User user = User.builder().id(userId).email("user@example.com").build();
    ReflectionTestUtils.setField(user, "createdAt", today.minusDays(6).atStartOfDay());

    MedicationBundle validBundle =
        MedicationBundle.builder()
            .user(user)
            .bundleName("vitamin")
            .dayOfWeek(today.getDayOfWeek().name())
            .scheduledTime(LocalTime.of(9, 0))
            .build();
    ReflectionTestUtils.setField(validBundle, "id", 10L);
    ReflectionTestUtils.setField(validBundle, "createdAt", today.minusDays(6).atStartOfDay());

    MedicationBundle malformedBundle =
        MedicationBundle.builder()
            .user(user)
            .bundleName("broken")
            .dayOfWeek("")
            .scheduledTime(LocalTime.of(10, 0))
            .build();
    ReflectionTestUtils.setField(malformedBundle, "id", 11L);
    ReflectionTestUtils.setField(malformedBundle, "createdAt", today.minusDays(6).atStartOfDay());

    MedicationLog log =
        MedicationLog.builder()
            .medicationBundle(validBundle)
            .date(today)
            .isTaken(true)
            .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findAllByUserId(userId))
        .willReturn(List.of(validBundle, malformedBundle));
    given(
            medicationLogRepository.findByMedicationBundle_UserAndDateBetween(
                eq(user), eq(today.minusDays(6)), eq(today)))
        .willReturn(List.of(log));

    ReportResponseDto.WeeklyReportResponse result =
        reportService.getWeeklyMedicationReport(userId);

    ReportResponseDto.DailyMedicationReportDto todayReport =
        result.getWeekData().get(result.getWeekData().size() - 1);
    assertThat(todayReport.getMedications()).hasSize(1);
    assertThat(todayReport.getMedications().get(0).getBundleName()).isEqualTo("vitamin");
  }

  @Test
  @DisplayName("getWeeklyEmotionReport returns 7 current and previous days when no data exists")
  void getWeeklyEmotionReport_withoutData() {
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(anyLong(), any(), any()))
        .willReturn(Collections.emptyList());
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                anyLong(), any(LocalDate.class), any(LocalDate.class), eq(true)))
        .willReturn(Collections.emptyList());

    ReportResponseDto.WeeklyEmotionReportResponse result =
        reportService.getWeeklyEmotionReport(1L);

    assertThat(result.getCurrentWeek()).hasSize(7);
    assertThat(result.getPreviousWeek()).hasSize(7);
    assertThat(result.getCurrentWeek())
        .allSatisfy(day -> assertThat(day.getDayOfWeek()).matches("^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)$"));
    assertThat(result.getCurrentWeek())
        .allSatisfy(day -> assertThat(day.getEmotionScore()).isEqualTo(0.0));
  }

  @Test
  @DisplayName("getWeeklyEmotionReport records report segment timers")
  void getWeeklyEmotionReport_recordsReportSegmentTimers() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    LocalDate rangeStart = today.minusDays(13);
    LocalDate rangeEnd = today;

    given(
            chatSessionRepository.findByUserIdAndStartedAtBetween(
                userId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay()))
        .willReturn(Collections.emptyList());
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                userId, rangeStart, rangeEnd, true))
        .willReturn(Collections.emptyList());

    reportService.getWeeklyEmotionReport(userId);

    assertThat(
            meterRegistry
                .find("chatbot.report.segment.duration")
                .tag("report", "weekly_emotion")
                .tag("segment", "total")
                .timer())
        .isNotNull();
    assertThat(
            meterRegistry
                .find("chatbot.report.segment.duration")
                .tag("report", "weekly_emotion")
                .tag("segment", "chat_session_query")
                .timer())
        .isNotNull();
    assertThat(
            meterRegistry
                .find("chatbot.report.segment.duration")
                .tag("report", "weekly_emotion")
                .tag("segment", "medication_log_query")
                .timer())
        .isNotNull();
  }

  @Test
  @DisplayName("getWeeklyEmotionReport fails when chatbot emotion source is unavailable")
  void getWeeklyEmotionReport_chatbotSourceFailure_throwsReportDataSourceUnavailable() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    LocalDate rangeStart = today.minusDays(13);
    LocalDate rangeEnd = today;

    given(
            chatSessionRepository.findByUserIdAndStartedAtBetween(
                userId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay()))
        .willThrow(new RuntimeException("mongo unavailable"));

    assertThatThrownBy(() -> reportService.getWeeklyEmotionReport(userId))
        .isInstanceOfSatisfying(
            GeneralException.class,
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isEqualTo(ErrorStatus.REPORT_DATA_SOURCE_UNAVAILABLE));

    assertThat(
            meterRegistry
                .find("chatbot.report.segment.failure")
                .tag("report", "weekly_emotion")
                .tag("segment", "chatbot_emotion_scores")
                .tag("source", "chatbot")
                .counter())
        .isNotNull();
    assertThat(
            meterRegistry
                .find("chatbot.report.segment.failure")
                .tag("report", "weekly_emotion")
                .tag("segment", "chatbot_emotion_scores")
                .tag("source", "chatbot")
                .counter()
                .count())
        .isEqualTo(1.0);
    then(reportConverter).should(never()).toWeeklyEmotionReport(any(), any());
  }

  @Test
  @DisplayName("getWeeklyEmotionReport fails when medication emotion source is unavailable")
  void getWeeklyEmotionReport_medicationSourceFailure_throwsReportDataSourceUnavailable() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    LocalDate rangeStart = today.minusDays(13);
    LocalDate rangeEnd = today;

    given(
            chatSessionRepository.findByUserIdAndStartedAtBetween(
                userId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay()))
        .willReturn(Collections.emptyList());
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                userId, rangeStart, rangeEnd, true))
        .willThrow(new RuntimeException("mysql unavailable"));

    assertThatThrownBy(() -> reportService.getWeeklyEmotionReport(userId))
        .isInstanceOfSatisfying(
            GeneralException.class,
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isEqualTo(ErrorStatus.REPORT_DATA_SOURCE_UNAVAILABLE));

    assertThat(
            meterRegistry
                .find("chatbot.report.segment.failure")
                .tag("report", "weekly_emotion")
                .tag("segment", "medication_emotion_scores")
                .tag("source", "medication")
                .counter())
        .isNotNull();
    assertThat(
            meterRegistry
                .find("chatbot.report.segment.failure")
                .tag("report", "weekly_emotion")
                .tag("segment", "medication_emotion_scores")
                .tag("source", "medication")
                .counter()
                .count())
        .isEqualTo(1.0);
    then(reportConverter).should(never()).toWeeklyEmotionReport(any(), any());
  }

  @Test
  @DisplayName("getWeeklyEmotionReport splits emotion message lookup into bounded session batches")
  void getWeeklyEmotionReport_batchesChatMessageLookupBySessionIds() {
    Long userId = 1L;
    LocalDate targetDate = LocalDate.now();
    LocalDate rangeStart = targetDate.minusDays(13);
    LocalDate rangeEnd = targetDate;
    List<ChatSession> sessions =
        IntStream.rangeClosed(1, 501)
            .mapToObj(
                index ->
                    ChatSession.builder()
                        .id("session-" + index)
                        .userId(userId)
                        .startedAt(targetDate.atStartOfDay().plusMinutes(index))
                        .build())
            .toList();
    List<String> firstBatch =
        IntStream.rangeClosed(1, 500).mapToObj(index -> "session-" + index).toList();
    List<String> secondBatch = List.of("session-501");
    ChatMessage joyMessage =
        ChatMessage.builder()
            .sessionId("session-500")
            .senderType(ChatMessage.SenderType.USER)
            .chatStep(ChatStep.EMOTION_SELECT.name())
            .responseCode("JOY")
            .build();
    ChatMessage excitedMessage =
        ChatMessage.builder()
            .sessionId("session-2")
            .senderType(ChatMessage.SenderType.USER)
            .chatStep(ChatStep.EMOTION_SELECT.name())
            .responseCode("EXCITED")
            .build();

    given(
            chatSessionRepository.findByUserIdAndStartedAtBetween(
                userId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay()))
        .willReturn(sessions);
    given(
            chatMessageRepository.findBySessionIdInAndSenderTypeAndChatStep(
                firstBatch,
                ChatMessage.SenderType.USER,
                ChatStep.EMOTION_SELECT.name()))
        .willReturn(List.of(joyMessage));
    given(
            chatMessageRepository.findBySessionIdInAndSenderTypeAndChatStep(
                secondBatch,
                ChatMessage.SenderType.USER,
                ChatStep.EMOTION_SELECT.name()))
        .willReturn(List.of(excitedMessage));
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                userId, rangeStart, rangeEnd, true))
        .willReturn(Collections.emptyList());

    ReportResponseDto.WeeklyEmotionReportResponse result =
        reportService.getWeeklyEmotionReport(userId);

    ReportResponseDto.DailyEmotionReportDto targetDay =
        result.getCurrentWeek().stream()
            .filter(day -> day.getDate().equals(targetDate.toString()))
            .findFirst()
            .orElseThrow();
    assertThat(targetDay.getEmotionScore()).isEqualTo(1.0);
    assertThat(
            meterRegistry
                .find("chatbot.report.segment.duration")
                .tag("report", "weekly_emotion")
                .tag("segment", "chat_message_query")
                .timer()
                .count())
        .isEqualTo(1L);
    then(chatMessageRepository)
        .should()
        .findBySessionIdInAndSenderTypeAndChatStep(
            firstBatch,
            ChatMessage.SenderType.USER,
            ChatStep.EMOTION_SELECT.name());
    then(chatMessageRepository)
        .should()
        .findBySessionIdInAndSenderTypeAndChatStep(
            secondBatch,
            ChatMessage.SenderType.USER,
            ChatStep.EMOTION_SELECT.name());
    then(chatMessageRepository)
        .should(never())
        .findBySessionIdAndSenderTypeAndChatStep(any(), any(), any());
  }

  @Test
  @DisplayName("getWeeklyEmotionReport fails when a batched emotion message lookup fails")
  void getWeeklyEmotionReport_batchedChatMessageFailure_throwsReportDataSourceUnavailable() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    LocalDate rangeStart = today.minusDays(13);
    LocalDate rangeEnd = today;
    List<ChatSession> sessions =
        IntStream.rangeClosed(1, 501)
            .mapToObj(
                index ->
                    ChatSession.builder()
                        .id("session-" + index)
                        .userId(userId)
                        .startedAt(today.atStartOfDay().plusMinutes(index))
                        .build())
            .toList();
    List<String> firstBatch =
        IntStream.rangeClosed(1, 500).mapToObj(index -> "session-" + index).toList();
    List<String> secondBatch = List.of("session-501");

    given(
            chatSessionRepository.findByUserIdAndStartedAtBetween(
                userId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay()))
        .willReturn(sessions);
    given(
            chatMessageRepository.findBySessionIdInAndSenderTypeAndChatStep(
                firstBatch,
                ChatMessage.SenderType.USER,
                ChatStep.EMOTION_SELECT.name()))
        .willReturn(Collections.emptyList());
    given(
            chatMessageRepository.findBySessionIdInAndSenderTypeAndChatStep(
                secondBatch,
                ChatMessage.SenderType.USER,
                ChatStep.EMOTION_SELECT.name()))
        .willThrow(new RuntimeException("mongo command too large"));

    assertThatThrownBy(() -> reportService.getWeeklyEmotionReport(userId))
        .isInstanceOfSatisfying(
            GeneralException.class,
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isEqualTo(ErrorStatus.REPORT_DATA_SOURCE_UNAVAILABLE));

    assertThat(
            meterRegistry
                .find("chatbot.report.segment.failure")
                .tag("report", "weekly_emotion")
                .tag("segment", "chatbot_emotion_scores")
                .tag("source", "chatbot")
                .counter())
        .isNotNull();
  }

  @Test
  @DisplayName("getWeeklyEmotionReport calculates the full 14-day range with one query per source")
  void getWeeklyEmotionReport_batchesFullRangeQueries() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    LocalDate targetDate = today.minusDays(3);
    LocalDate rangeStart = today.minusDays(13);
    LocalDate rangeEnd = today;
    ChatSession session =
        ChatSession.builder()
            .id("session-1")
            .userId(userId)
            .startedAt(targetDate.atTime(9, 0))
            .build();
    ChatMessage message =
        ChatMessage.builder()
            .sessionId("session-1")
            .senderType(ChatMessage.SenderType.USER)
            .chatStep(ChatStep.EMOTION_SELECT.name())
            .responseCode("JOY")
            .build();
    MedicationBundle bundle =
        MedicationBundle.builder()
            .bundleName("vitamin")
            .dayOfWeek(targetDate.getDayOfWeek().name())
            .scheduledTime(LocalTime.of(9, 0))
            .build();
    MedicationLog medicationLog =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(targetDate)
            .isTaken(true)
            .medCondition(-1)
            .build();

    given(
            chatSessionRepository.findByUserIdAndStartedAtBetween(
                userId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay()))
        .willReturn(List.of(session));
    given(
            chatMessageRepository.findBySessionIdInAndSenderTypeAndChatStep(
                List.of("session-1"),
                ChatMessage.SenderType.USER,
                ChatStep.EMOTION_SELECT.name()))
        .willReturn(List.of(message));
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                userId, rangeStart, rangeEnd, true))
        .willReturn(List.of(medicationLog));

    ReportResponseDto.WeeklyEmotionReportResponse result =
        reportService.getWeeklyEmotionReport(userId);

    ReportResponseDto.DailyEmotionReportDto targetDay =
        result.getCurrentWeek().stream()
            .filter(day -> day.getDate().equals(targetDate.toString()))
            .findFirst()
            .orElseThrow();
    assertThat(targetDay.getEmotionScore()).isEqualTo(0.0);
    then(chatSessionRepository)
        .should()
        .findByUserIdAndStartedAtBetween(
            userId, rangeStart.atStartOfDay(), rangeEnd.plusDays(1).atStartOfDay());
    then(chatSessionRepository).shouldHaveNoMoreInteractions();
    then(chatMessageRepository)
        .should()
        .findBySessionIdInAndSenderTypeAndChatStep(
            List.of("session-1"),
            ChatMessage.SenderType.USER,
            ChatStep.EMOTION_SELECT.name());
    then(chatMessageRepository).shouldHaveNoMoreInteractions();
    then(medicationLogRepository)
        .should()
        .findByUserIdAndDateBetweenAndIsTaken(userId, rangeStart, rangeEnd, true);
  }

  @Test
  @DisplayName("getWeeklyReportSummary calculates emotion change with one range query per source")
  void getWeeklyReportSummary_batchesEmotionChangeQueries() {
    Long userId = 1L;
    LocalDate today = LocalDate.now();
    LocalDate lastWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(1);
    LocalDate lastWeekEnd = lastWeekStart.plusDays(6);
    LocalDate twoWeeksAgoStart = lastWeekStart.minusWeeks(1);
    LocalDate twoWeeksAgoEnd = twoWeeksAgoStart.plusDays(6);
    User user = User.builder().id(userId).email("user@example.com").build();
    ReflectionTestUtils.setField(user, "createdAt", twoWeeksAgoStart.minusWeeks(2).atStartOfDay());

    MedicationBundle bundle =
        MedicationBundle.builder()
            .user(user)
            .bundleName("vitamin")
            .dayOfWeek(lastWeekStart.getDayOfWeek().name())
            .scheduledTime(LocalTime.of(9, 0))
            .build();
    ReflectionTestUtils.setField(bundle, "id", 10L);
    ReflectionTestUtils.setField(bundle, "createdAt", twoWeeksAgoStart.minusWeeks(2).atStartOfDay());

    MedicationLog lastWeekEmotion =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(lastWeekStart)
            .isTaken(true)
            .medCondition(1)
            .build();
    MedicationLog twoWeeksAgoEmotion =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(twoWeeksAgoStart)
            .isTaken(true)
            .medCondition(1)
            .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(
            chatSessionRepository.findByUserIdAndStartedAtBetween(
                userId, twoWeeksAgoStart.atStartOfDay(), lastWeekEnd.plusDays(1).atStartOfDay()))
        .willReturn(Collections.emptyList());
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                userId, twoWeeksAgoStart, lastWeekEnd, true))
        .willReturn(List.of(lastWeekEmotion, twoWeeksAgoEmotion));
    given(medicationBundleRepository.findByUserIdAndIsDeletedFalse(userId)).willReturn(List.of(bundle));
    given(
            medicationLogRepository.countByBundleIdsAndDateRangeAndIsTaken(
                List.of(10L), lastWeekStart, lastWeekEnd, true))
        .willReturn(1L);

    ReportResponseDto.WeeklyReportSummaryResponse result =
        reportService.getWeeklyReportSummary(userId);

    assertThat(result.getHasEmotionData()).isTrue();
    assertThat(result.getEmotionImprovementRate()).isEqualTo(0.0);
    then(chatSessionRepository)
        .should()
        .findByUserIdAndStartedAtBetween(
            userId, twoWeeksAgoStart.atStartOfDay(), lastWeekEnd.plusDays(1).atStartOfDay());
    then(chatSessionRepository).shouldHaveNoMoreInteractions();
    then(medicationLogRepository)
        .should()
        .findByUserIdAndDateBetweenAndIsTaken(userId, twoWeeksAgoStart, lastWeekEnd, true);
    then(medicationLogRepository)
        .should(never())
        .findByUserIdAndDateAndIsTaken(anyLong(), any(LocalDate.class), eq(true));
  }

  @Test
  @DisplayName("getMonthlyEmotionReport returns null emotionScore when a day has no emotion inputs")
  void getMonthlyEmotionReport_withoutData_returnsNullEmotionScore() {
    YearMonth targetMonth = YearMonth.now();
    LocalDate startDate = targetMonth.atDay(1);
    LocalDate endDate = targetMonth.atEndOfMonth();
    User user = monthlyUser(1L);
    LocalDate summaryDate = LocalDate.now();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(anyLong(), any(), any()))
        .willReturn(Collections.emptyList());
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                1L, startDate, endDate, true))
        .willReturn(Collections.emptyList());
    given(diaryEmotionScoreReader.findWeightedEmotionScoresByDate(1L, startDate, endDate))
        .willReturn(Collections.emptyMap());
    given(summaryCacheRepository.findByUserIdAndSummaryDate(1L, summaryDate))
        .willReturn(Optional.empty());
    given(diaryTagSummaryReader.readRecentTagSummary(user, summaryDate.minusDays(2), summaryDate.minusDays(1)))
        .willReturn(RecentDiaryTagSummary.empty());

    ReportResponseDto.MonthlyEmotionReportResponse result =
        reportService.getMonthlyEmotionReport(1L, 0);

    assertThat(result.getSummary().getHasRecentDiaryData()).isFalse();
    assertThat(result.getSummary().getContent())
        .isEqualTo(DiaryAiService.noRecentDiarySummary("테스트닉네임"));
    assertThat(result.getMonthData()).isNotEmpty();
    assertThat(result.getMonthData())
        .allSatisfy(day -> assertThat(day.getEmotionScore()).isNull());
    then(diaryAiService).should(never()).generateMonthlySummary(any(), anyString());
  }

  @Test
  @DisplayName("getMonthlyEmotionReport keeps zero when the recorded emotion average is neutral")
  void getMonthlyEmotionReport_withNeutralInput_keepsZero() {
    LocalDate targetDate = LocalDate.now().withDayOfMonth(1);
    YearMonth targetMonth = YearMonth.now();
    LocalDate summaryDate = LocalDate.now();
    User user = monthlyUser(1L);
    MedicationBundle bundle =
        MedicationBundle.builder()
            .bundleName("vitamin")
            .dayOfWeek(targetDate.getDayOfWeek().name())
            .scheduledTime(LocalTime.of(9, 0))
            .build();
    ReflectionTestUtils.setField(bundle, "id", 10L);

    MedicationLog neutralLog =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(targetDate)
            .isTaken(true)
            .medCondition(0)
            .build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(anyLong(), any(), any()))
        .willReturn(Collections.emptyList());
    given(
            medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
                1L,
                targetDate.withDayOfMonth(1),
                targetDate.withDayOfMonth(targetDate.lengthOfMonth()),
                true))
        .willReturn(List.of(neutralLog));
    given(diaryEmotionScoreReader.findWeightedEmotionScoresByDate(
            1L, targetMonth.atDay(1), targetMonth.atEndOfMonth()))
        .willReturn(Collections.emptyMap());
    given(summaryCacheRepository.findByUserIdAndSummaryDate(1L, summaryDate))
        .willReturn(Optional.of(summaryCache(user, summaryDate, false, "캐시 요약")));

    ReportResponseDto.MonthlyEmotionReportResponse result =
        reportService.getMonthlyEmotionReport(1L, 0);

    ReportResponseDto.DailyEmotionReportDto targetDay =
        result.getMonthData().stream()
            .filter(day -> day.getDate().equals(targetDate.toString()))
            .findFirst()
            .orElseThrow();

    assertThat(targetDay.getEmotionScore()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("getMonthlyEmotionReport includes one-line diary score with double weight")
  void getMonthlyEmotionReport_includesDiaryScoreWithDoubleWeight() {
    Long userId = 1L;
    User user = monthlyUser(userId);
    YearMonth targetMonth = YearMonth.now();
    LocalDate startDate = targetMonth.atDay(1);
    LocalDate endDate = targetMonth.atEndOfMonth();
    LocalDate targetDate = startDate;
    LocalDate summaryDate = LocalDate.now();
    ChatSession session =
        ChatSession.builder()
            .id("session-1")
            .userId(userId)
            .startedAt(targetDate.atTime(9, 0))
            .build();
    ChatMessage message =
        ChatMessage.builder()
            .sessionId("session-1")
            .senderType(ChatMessage.SenderType.USER)
            .chatStep(ChatStep.EMOTION_SELECT.name())
            .responseCode("JOY")
            .build();
    MedicationLog medicationLog =
        MedicationLog.builder()
            .date(targetDate)
            .isTaken(true)
            .medCondition(-1)
            .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(
            userId, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()))
        .willReturn(List.of(session));
    given(chatMessageRepository.findBySessionIdInAndSenderTypeAndChatStep(
            List.of("session-1"),
            ChatMessage.SenderType.USER,
            ChatStep.EMOTION_SELECT.name()))
        .willReturn(List.of(message));
    given(medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
            userId, startDate, endDate, true))
        .willReturn(List.of(medicationLog));
    given(diaryEmotionScoreReader.findWeightedEmotionScoresByDate(userId, startDate, endDate))
        .willReturn(Collections.singletonMap(targetDate, List.of(2, 2)));
    given(summaryCacheRepository.findByUserIdAndSummaryDate(userId, summaryDate))
        .willReturn(Optional.of(summaryCache(user, summaryDate, true, "캐시 요약")));

    ReportResponseDto.MonthlyEmotionReportResponse result =
        reportService.getMonthlyEmotionReport(userId, 0);

    ReportResponseDto.DailyEmotionReportDto targetDay =
        result.getMonthData().stream()
            .filter(day -> day.getDate().equals(targetDate.toString()))
            .findFirst()
            .orElseThrow();
    assertThat(targetDay.getEmotionScore()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("getMonthlyEmotionReport reuses summary cache without AI call")
  void getMonthlyEmotionReport_reusesSummaryCache() {
    Long userId = 1L;
    User user = monthlyUser(userId);
    YearMonth targetMonth = YearMonth.now();
    LocalDate summaryDate = LocalDate.now();
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(anyLong(), any(), any()))
        .willReturn(Collections.emptyList());
    given(medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
            userId, targetMonth.atDay(1), targetMonth.atEndOfMonth(), true))
        .willReturn(Collections.emptyList());
    given(diaryEmotionScoreReader.findWeightedEmotionScoresByDate(
            userId, targetMonth.atDay(1), targetMonth.atEndOfMonth()))
        .willReturn(Collections.emptyMap());
    given(summaryCacheRepository.findByUserIdAndSummaryDate(userId, summaryDate))
        .willReturn(Optional.of(summaryCache(user, summaryDate, true, "이미 만든 요약")));

    ReportResponseDto.MonthlyEmotionReportResponse result =
        reportService.getMonthlyEmotionReport(userId, 0);

    assertThat(result.getSummary().getHasRecentDiaryData()).isTrue();
    assertThat(result.getSummary().getContent()).isEqualTo("이미 만든 요약");
    then(diaryTagSummaryReader).should(never()).readRecentTagSummary(any(), any(), any());
    then(diaryAiService).should(never()).generateMonthlySummary(any(), anyString());
  }

  @Test
  @DisplayName("getMonthlyEmotionReport fails when diary emotion source is unavailable")
  void getMonthlyEmotionReport_diarySourceFailure_throwsReportDataSourceUnavailable() {
    Long userId = 1L;
    User user = monthlyUser(userId);
    YearMonth targetMonth = YearMonth.now();
    LocalDate startDate = targetMonth.atDay(1);
    LocalDate endDate = targetMonth.atEndOfMonth();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(anyLong(), any(), any()))
        .willReturn(Collections.emptyList());
    given(medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
            userId, startDate, endDate, true))
        .willReturn(Collections.emptyList());
    given(diaryEmotionScoreReader.findWeightedEmotionScoresByDate(userId, startDate, endDate))
        .willThrow(new RuntimeException("diary db unavailable"));

    assertThatThrownBy(() -> reportService.getMonthlyEmotionReport(userId, 0))
        .isInstanceOfSatisfying(
            GeneralException.class,
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isEqualTo(ErrorStatus.REPORT_DATA_SOURCE_UNAVAILABLE));

    then(reportConverter).should(never()).toMonthlyEmotionReport(any(), any(), any());
  }

  @Test
  @DisplayName("getMonthlyEmotionReport uses the user's timezone for target month and summary date")
  void getMonthlyEmotionReport_usesUserTimezoneForTargetMonthAndSummaryDate() {
    Long userId = 1L;
    User user = monthlyUser(userId);
    ReflectionTestUtils.setField(user, "timeZone", "America/Los_Angeles");
    YearMonth targetMonth = YearMonth.of(2026, 5);
    LocalDate summaryDate = LocalDate.of(2026, 5, 1);

    given(appDateTimeProvider.currentMonth("America/Los_Angeles")).willReturn(targetMonth);
    given(appDateTimeProvider.today("America/Los_Angeles")).willReturn(summaryDate);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(anyLong(), any(), any()))
        .willReturn(Collections.emptyList());
    given(medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
            userId, targetMonth.atDay(1), targetMonth.atEndOfMonth(), true))
        .willReturn(Collections.emptyList());
    given(diaryEmotionScoreReader.findWeightedEmotionScoresByDate(
            userId, targetMonth.atDay(1), targetMonth.atEndOfMonth()))
        .willReturn(Collections.emptyMap());
    given(summaryCacheRepository.findByUserIdAndSummaryDate(userId, summaryDate))
        .willReturn(Optional.of(summaryCache(user, summaryDate, false, "캐시 요약")));

    ReportResponseDto.MonthlyEmotionReportResponse result =
        reportService.getMonthlyEmotionReport(userId, 0);

    assertThat(result.getYear()).isEqualTo(2026);
    assertThat(result.getMonth()).isEqualTo(5);
    then(appDateTimeProvider).should().currentMonth("America/Los_Angeles");
    then(appDateTimeProvider).should().today("America/Los_Angeles");
  }

  @Test
  @DisplayName("월간 요약 AI fallback도 같은 날짜 캐시에 저장한다")
  void getMonthlyEmotionReport_storesAiFallbackSummaryInCache() {
    Long userId = 1L;
    User user = monthlyUser(userId);
    YearMonth targetMonth = YearMonth.of(2026, 5);
    LocalDate summaryDate = LocalDate.of(2026, 5, 3);
    RecentDiaryTagSummary tagSummary = new RecentDiaryTagSummary(
        true,
        List.of(new RecentDiaryTagSummary.TagFrequency("LIFE", "FATIGUE", "피곤함", 2)),
        List.of(new RecentDiaryTagSummary.DailyTagGroup(summaryDate.minusDays(1), List.of("피곤함"))));

    given(appDateTimeProvider.currentMonth("Asia/Seoul")).willReturn(targetMonth);
    given(appDateTimeProvider.today("Asia/Seoul")).willReturn(summaryDate);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(chatSessionRepository.findByUserIdAndStartedAtBetween(anyLong(), any(), any()))
        .willReturn(Collections.emptyList());
    given(medicationLogRepository.findByUserIdAndDateBetweenAndIsTaken(
            userId, targetMonth.atDay(1), targetMonth.atEndOfMonth(), true))
        .willReturn(Collections.emptyList());
    given(diaryEmotionScoreReader.findWeightedEmotionScoresByDate(
            userId, targetMonth.atDay(1), targetMonth.atEndOfMonth()))
        .willReturn(Collections.emptyMap());
    given(summaryCacheRepository.findByUserIdAndSummaryDate(userId, summaryDate))
        .willReturn(Optional.empty());
    given(diaryTagSummaryReader.readRecentTagSummary(
            user, summaryDate.minusDays(2), summaryDate.minusDays(1)))
        .willReturn(tagSummary);
    given(diaryAiService.generateMonthlySummary(tagSummary, "테스트닉네임"))
        .willReturn(DiaryAiService.FALLBACK_MONTHLY_SUMMARY);

    ReportResponseDto.MonthlyEmotionReportResponse result =
        reportService.getMonthlyEmotionReport(userId, 0);

    ArgumentCaptor<DiaryReportSummaryCache> cacheCaptor =
        ArgumentCaptor.forClass(DiaryReportSummaryCache.class);
    then(summaryCacheRepository).should().save(cacheCaptor.capture());
    assertThat(result.getSummary().getContent()).isEqualTo(DiaryAiService.FALLBACK_MONTHLY_SUMMARY);
    assertThat(cacheCaptor.getValue().getContent())
        .isEqualTo(DiaryAiService.FALLBACK_MONTHLY_SUMMARY);
  }

  private User monthlyUser(Long userId) {
    return User.builder()
        .id(userId)
        .email("user@example.com")
        .fullName("Test User")
        .nickname("테스트닉네임")
        .timeZone("Asia/Seoul")
        .build();
  }

  private DiaryReportSummaryCache summaryCache(
      User user, LocalDate summaryDate, boolean hasRecentDiaryData, String content) {
    return DiaryReportSummaryCache.builder()
        .user(user)
        .summaryDate(summaryDate)
        .hasRecentDiaryData(hasRecentDiaryData)
        .content(content)
        .build();
  }
}
