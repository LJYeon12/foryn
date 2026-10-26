package com.forA.chatbot.report.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.NotificationHandler;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.repository.UserRepository;
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
import com.forA.chatbot.report.dto.ReportResponseDto.MonthlyEmotionReportResponse;
import com.forA.chatbot.user.domain.User;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

  private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final UserRepository userRepository;
  private final MedicationBundleRepository medicationBundleRepository;
  private final MedicationLogRepository medicationLogRepository;
  private final ReportConverter reportConverter;
  private final ReportMetricsRecorder reportMetricsRecorder;
  private final EmotionScoreCalculator emotionScoreCalculator;
  private final DiaryTagSummaryReader diaryTagSummaryReader;
  private final DiaryReportSummaryCacheRepository summaryCacheRepository;
  private final DiaryAiService diaryAiService;
  private final AppDateTimeProvider appDateTimeProvider;

  public ReportResponseDto.WeeklyReportResponse getWeeklyMedicationReport(Long userId) {
    // 1. 사용자 정보 조회
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

    LocalDate today = LocalDate.now();
    LocalDate startDate = today.minusDays(6);

    // 공통 메서드를 호출하여 주간 데이터 생성
    List<ReportResponseDto.DailyMedicationReportDto> weekData = generateDailyReports(user, startDate, today);

    return reportConverter.toWeeklyMedicationReport(weekData);
  }


  public ReportResponseDto.MonthlyReportResponse getMonthlyMedicationReport(Long userId, int monthOffset) {
      User user =
          userRepository
              .findById(userId)
              .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

      // 1. monthOffset을 기준으로 대상 월의 시작일과 종료일 계산
      YearMonth targetMonth =
          appDateTimeProvider.currentMonth(zoneIdOf(user).getId()).plusMonths(monthOffset);
      LocalDate startDate = targetMonth.atDay(1);
      LocalDate endDate = targetMonth.atEndOfMonth();

      // 2. 공통 로직을 사용하여 일별 리포트 데이터 생성
      List<ReportResponseDto.DailyMedicationReportDto> monthData = generateDailyReports(user, startDate, endDate);

      return reportConverter.toMonthlyMedicationReport(targetMonth, monthData);
  }

  /**
   * 지정된 기간 동안의 일별 복용 리포트 목록을 생성 (필터링 로직 강화)
   */
  private List<ReportResponseDto.DailyMedicationReportDto> generateDailyReports(User user, LocalDate startDate, LocalDate endDate) {
    // 기준일 설정: 가입일 이전 데이터는 아예 생성하지 않음
    LocalDate userJoinDate = user.getCreatedAt().toLocalDate();
    LocalDate actualStartDate = startDate.isBefore(userJoinDate) ? userJoinDate : startDate;

    // 번들 조회: 과거 기록을 위해 '삭제된 번들'도 포함해서 가져와야 함
    List<MedicationBundle> allBundles = medicationBundleRepository.findAllByUserId(user.getId());

    // 복용 로그 조회
    List<MedicationLog> logs = medicationLogRepository.findByMedicationBundle_UserAndDateBetween(user, actualStartDate, endDate);
    Map<LocalDate, Map<Long, MedicationLog>> logMap = logs.stream()
        .collect(Collectors.groupingBy(
            MedicationLog::getDate,
            Collectors.toMap(log -> log.getMedicationBundle().getId(), log -> log, (existing, replacement) -> existing)
        ));

    List<ReportResponseDto.DailyMedicationReportDto> dailyReports = new ArrayList<>();

    // 시작일부터 종료일까지 반복 (실제 시작일 기준)
    for (LocalDate date = actualStartDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      DayOfWeek dayOfWeekEnum = date.getDayOfWeek();
      Map<Long, MedicationLog> dailyLogs = logMap.getOrDefault(date, Collections.emptyMap());

      final LocalDate targetDate = date;

      // 이 날짜에 유효한 번들만 필터링
      List<ReportResponseDto.MedicationStatusDto> medications = allBundles.stream()
          .filter(bundle -> isScheduledForDay(bundle, dayOfWeekEnum)) // 요일 체크
          .filter(bundle -> !targetDate.isBefore(bundle.getCreatedAt().toLocalDate())) // 추가 전 날짜 필터링
          .filter(bundle -> {
            // 삭제된 약 처리: 삭제되지 않았거나, 삭제된 날짜가 현재 순회 중인 date보다 뒤인 경우 포함
            if (!bundle.getIsDeleted()) return true;
            if (bundle.getDeletedAt() == null) return false;
            return !targetDate.isAfter(bundle.getDeletedAt().toLocalDate());
          })
          .map(bundle -> reportConverter.toMedicationStatus(bundle, dailyLogs.get(bundle.getId())))
          .collect(Collectors.toList());

      dailyReports.add(reportConverter.toDailyMedicationReport(date, dayOfWeekEnum, medications));
    }
    return dailyReports;
  }

  // 요일 포함 여부 체크 헬퍼 메서드
  private boolean isScheduledForDay(MedicationBundle bundle, DayOfWeek day) {
    return parseDayOfWeek(bundle).contains(day);
  }

  /**
   * 주간 감정 리포트 조회
   * - currentWeek: 오늘 포함 이전 7일
   * - previousWeek: currentWeek 이전 7일
   */
  public ReportResponseDto.WeeklyEmotionReportResponse getWeeklyEmotionReport(Long userId) {
    return reportMetricsRecorder.recordSegment("weekly_emotion", "total", () -> {
      LocalDate today = LocalDate.now();

    // 현재 주 데이터 (오늘 포함 이전 7일)
      LocalDate currentWeekStart = today.minusDays(6);
      LocalDate currentWeekEnd = today;
      LocalDate previousWeekStart = currentWeekStart.minusDays(7);
      LocalDate previousWeekEnd = currentWeekStart.minusDays(1);

      Map<LocalDate, Double> emotionScoresByDate =
          emotionScoreCalculator.calculateEmotionScoresByDate(
              "weekly_emotion", userId, previousWeekStart, currentWeekEnd);
      List<ReportResponseDto.DailyEmotionReportDto> currentWeekData =
          buildDailyEmotionReports(emotionScoresByDate, currentWeekStart, currentWeekEnd, 0.0);
      List<ReportResponseDto.DailyEmotionReportDto> previousWeekData =
          buildDailyEmotionReports(emotionScoresByDate, previousWeekStart, previousWeekEnd, 0.0);

      return reportConverter.toWeeklyEmotionReport(currentWeekData, previousWeekData);
    });
  }

  /**
   * 특정 기간 동안의 일별 감정 점수 계산
   */
  private List<ReportResponseDto.DailyEmotionReportDto> calculateDailyEmotionScores(
      String reportName,
      Long userId,
      LocalDate startDate,
      LocalDate endDate,
      Double emptyScoreFallback) {
    Map<LocalDate, Double> emotionScoresByDate =
        emotionScoreCalculator.calculateEmotionScoresByDate(reportName, userId, startDate, endDate);
    return buildDailyEmotionReports(emotionScoresByDate, startDate, endDate, emptyScoreFallback);
  }

  private List<ReportResponseDto.DailyEmotionReportDto> buildDailyEmotionReports(
      Map<LocalDate, Double> emotionScoresByDate,
      LocalDate startDate,
      LocalDate endDate,
      Double emptyScoreFallback) {
    List<ReportResponseDto.DailyEmotionReportDto> dailyDataList = new ArrayList<>();

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      Double emotionScore = emotionScoresByDate.get(date);
      if (emotionScore == null) {
        emotionScore = emptyScoreFallback;
      }

      dailyDataList.add(
          reportConverter.toDailyEmotionReport(
              date, getDayOfWeekInEnglish(date), emotionScore));
    }

    return dailyDataList;
  }

  private String getDayOfWeekInEnglish(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase();
  }

  private ZoneId zoneIdOf(User user) {
    try {
      return user.getTimeZone() == null || user.getTimeZone().isBlank()
          ? DEFAULT_ZONE_ID
          : ZoneId.of(user.getTimeZone());
    } catch (DateTimeException e) {
      log.warn("사용자 timezone 값이 올바르지 않아 기본 timezone을 사용합니다. userId={}", user.getId());
      return DEFAULT_ZONE_ID;
    }
  }

  public MonthlyEmotionReportResponse getMonthlyEmotionReport(Long userId, int monthOffset) {
    return reportMetricsRecorder.recordSegment("monthly_emotion", "total", () -> {
      User user =
          userRepository
              .findById(userId)
              .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
      ZoneId userZoneId = zoneIdOf(user);
      String userTimeZone = userZoneId.getId();
      LocalDate summaryDate = appDateTimeProvider.today(userTimeZone);
    // 1. monthOffset을 기준으로 대상 월의 시작일과 종료일 계산
      YearMonth targetMonth = appDateTimeProvider.currentMonth(userTimeZone).plusMonths(monthOffset);
      LocalDate startDate = targetMonth.atDay(1);
      LocalDate endDate = targetMonth.atEndOfMonth();

    // 2. 공통 로직을 사용하여 일별 리포트 데이터 생성
      // 월간 리포트만 한줄일기 감정 점수를 포함한다. 주간 리포트는 기존 정책을 유지한다.
      Map<LocalDate, Double> emotionScoresByDate =
          emotionScoreCalculator.calculateEmotionScoresByDate(
              "monthly_emotion", userId, startDate, endDate, true);
      List<ReportResponseDto.DailyEmotionReportDto> monthData =
          buildDailyEmotionReports(emotionScoresByDate, startDate, endDate, null);
      ReportResponseDto.MonthlyEmotionSummaryDto summary =
          resolveMonthlyEmotionSummary(user, summaryDate);

      return reportConverter.toMonthlyEmotionReport(targetMonth, summary, monthData);
    });

  }

  private ReportResponseDto.MonthlyEmotionSummaryDto resolveMonthlyEmotionSummary(
      User user, LocalDate summaryDate) {
    return summaryCacheRepository.findByUserIdAndSummaryDate(user.getId(), summaryDate)
        .map(this::toMonthlyEmotionSummary)
        .orElseGet(() -> createAndCacheMonthlyEmotionSummary(user, summaryDate));
  }

  private ReportResponseDto.MonthlyEmotionSummaryDto createAndCacheMonthlyEmotionSummary(
      User user, LocalDate summaryDate) {
    LocalDate startDate = summaryDate.minusDays(2);
    LocalDate endDate = summaryDate.minusDays(1);
    RecentDiaryTagSummary tagSummary =
        diaryTagSummaryReader.readRecentTagSummary(user, startDate, endDate);
    boolean hasRecentDiaryData = tagSummary.hasRecentDiaryData();
    String content;
    if (!hasRecentDiaryData) {
      content = DiaryAiService.noRecentDiarySummary(user.getNickname());
    } else if (!tagSummary.hasTags()) {
      content = DiaryAiService.FALLBACK_MONTHLY_SUMMARY;
    } else {
      content = diaryAiService.generateMonthlySummary(tagSummary, user.getNickname());
    }

    // 같은 현지 날짜에는 동일 요약을 재사용하기 위해 대체 응답까지 DB 캐시에 저장한다.
    DiaryReportSummaryCache cache = DiaryReportSummaryCache.builder()
        .user(user)
        .summaryDate(summaryDate)
        .hasRecentDiaryData(hasRecentDiaryData)
        .content(content)
        .build();
    summaryCacheRepository.save(cache);
    return toMonthlyEmotionSummary(cache);
  }

  private ReportResponseDto.MonthlyEmotionSummaryDto toMonthlyEmotionSummary(
      DiaryReportSummaryCache cache) {
    return ReportResponseDto.MonthlyEmotionSummaryDto.builder()
        .hasRecentDiaryData(cache.hasRecentDiaryData())
        .content(cache.getContent())
        .build();
  }

  public ReportResponseDto.WeeklyReportSummaryResponse getWeeklyReportSummary(Long userId) {
    return reportMetricsRecorder.recordSegment("weekly_summary", "total", () -> {
    // 1. 사용자 정보 조회
      User user =
          userRepository
              .findById(userId)
              .orElseThrow(() -> new NotificationHandler(ErrorStatus.USER_NOT_FOUND));


      LocalDate today = LocalDate.now();
    // 감정 점수 변화율
      Double emotionRate = calculateEmotionScoreChangeRate(userId, user.getCreatedAt().toLocalDate(), today);
    // 저번주 복약 비율
      Double medicationRate = calculateLastWeekMedicationRate(userId, user.getCreatedAt().toLocalDate(), today);


      return reportConverter.toWeeklyReportSummary(emotionRate, medicationRate);
    });

  }

  /**
   * 저번주 복약 비율 계산
   */
  private Double calculateLastWeekMedicationRate(Long userId, LocalDate joinDate, LocalDate today) {
    // 저번주 기간 계산 (월요일~일요일)
    LocalDate lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

    // 신규 사용자 체크: 가입 후 1주일 데이터 필요
    LocalDate minimumDataDate = getMinimumDataDateForMedication(joinDate);
    if (today.isBefore(minimumDataDate)) {
      log.debug("복약 데이터 부족 - userId: {}, 최소 요구일: {}", userId, minimumDataDate);
      return null;
    }

    // 사용자의 활성화된 MedicationBundle 목록 조회
    List<MedicationBundle> bundles = medicationBundleRepository
        .findByUserIdAndIsDeletedFalse(userId);

    if (bundles.isEmpty()) {
      log.debug("복약 계획 없음 - userId: {}", userId);
      return null;
    }

    // 저번주에 먹어야 했던 총 횟수 계산
    int expectedCount = calculateExpectedMedicationCount(bundles, lastWeekStart, lastWeekEnd);

    if (expectedCount == 0) {
      log.debug("저번주 복약 예정 없음 - userId: {}", userId);
      return null;
    }

    // 실제로 복용한 횟수 계산
    Long actualCount = medicationLogRepository.countByBundleIdsAndDateRangeAndIsTaken(
        bundles.stream().map(MedicationBundle::getId).collect(Collectors.toList()),
        lastWeekStart,
        lastWeekEnd,
        true
    );

    // 복약 비율 계산: (실제 복용 / 예상 복용) * 100
    double rate = ((double) actualCount / expectedCount) * 100;
    return Math.round(rate * 100.0) / 100.0; // 소수점 2자리
  }

  /**
   * 감정 점수 변화율 계산
   * 지난주와 그 전주(2주 전) 비교
   */
  /**
   * 감정 점수 변화율 계산
   * 지난주와 그 전주(2주 전) 비교
   */
  private Double calculateEmotionScoreChangeRate(Long userId, LocalDate joinDate, LocalDate today) {
    // 저번주 기간 계산 (월요일~일요일)
    LocalDate lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

    // 그 전주 기간 계산
    LocalDate twoWeeksAgoStart = lastWeekStart.minusWeeks(1);
    LocalDate twoWeeksAgoEnd = twoWeeksAgoStart.plusDays(6);

    // 신규 사용자 체크: 가입 후 2주 데이터 필요
    LocalDate minimumDataDate = getMinimumDataDateForEmotion(joinDate);
    if (today.isBefore(minimumDataDate)) {
      log.debug("감정 데이터 부족 - userId: {}, 최소 요구일: {}", userId, minimumDataDate);
      return null;
    }

    Map<LocalDate, Double> emotionScoresByDate =
        emotionScoreCalculator.calculateEmotionScoresByDate(
            "weekly_summary", userId, twoWeeksAgoStart, lastWeekEnd);
    Double lastWeekAvg = calculateEmotionScoreAverage(emotionScoresByDate, lastWeekStart, lastWeekEnd);
    Double twoWeeksAgoAvg =
        calculateEmotionScoreAverage(emotionScoresByDate, twoWeeksAgoStart, twoWeeksAgoEnd);

    // 데이터가 없으면 null 반환
    if (lastWeekAvg == null || twoWeeksAgoAvg == null || twoWeeksAgoAvg == 0) {
      log.debug("감정 데이터 계산 불가 - userId: {}, lastWeek: {}, twoWeeksAgo: {}",
          userId, lastWeekAvg, twoWeeksAgoAvg);
      return null;
    }

    // 변화율 계산: (Scurr - Sprev) / |Sprev| * 100
    double changeRate = ((lastWeekAvg - twoWeeksAgoAvg) / Math.abs(twoWeeksAgoAvg)) * 100;
    return Math.round(changeRate * 100.0) / 100.0; // 소수점 2자리
  }

  private LocalDate getMinimumDataDateForMedication(LocalDate joinDate) {
    // 가입일이 속한 주의 일요일 찾기
    LocalDate firstWeekSunday = joinDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

    // 다음주 월요일
    return firstWeekSunday.plusDays(1);
  }

  /**
   * 저번주에 먹어야 하는 총 횟수 계산
   * dayOfWeek 필드를 파싱하여 저번주에 해당하는 요일 개수를 합산
   */
  private int calculateExpectedMedicationCount(
      List<MedicationBundle> bundles,
      LocalDate weekStart,
      LocalDate weekEnd) {

    int totalCount = 0;

    for (MedicationBundle bundle : bundles) {
      String dayOfWeek = bundle.getDayOfWeek();
      if (dayOfWeek == null || dayOfWeek.isEmpty()) {
        continue;
      }

      // dayOfWeek 파싱 (예: "MON,WED,FRI")
      Set<DayOfWeek> scheduledDays = parseDayOfWeek(dayOfWeek);

      // 저번주(월~일) 동안 해당 요일이 몇 번 나오는지 계산
      LocalDate currentDate = weekStart;
      while (!currentDate.isAfter(weekEnd)) {
        if (scheduledDays.contains(currentDate.getDayOfWeek())) {
          totalCount++;
        }
        currentDate = currentDate.plusDays(1);
      }
    }

    return totalCount;
  }

  /**
   * dayOfWeek 문자열을 DayOfWeek 집합으로 변환
   * 예: "MONDAY,WEDNESDAY,FRIDAY" -> {MONDAY, WEDNESDAY, FRIDAY}
   */
  private Set<DayOfWeek> parseDayOfWeek(String dayOfWeek) {
    return parseDayOfWeek(dayOfWeek, null);
  }

  private Set<DayOfWeek> parseDayOfWeek(MedicationBundle bundle) {
    return parseDayOfWeek(bundle.getDayOfWeek(), bundle.getId());
  }

  private Set<DayOfWeek> parseDayOfWeek(String dayOfWeek, Long bundleId) {
    if (dayOfWeek == null || dayOfWeek.isBlank()) {
      return Collections.emptySet();
    }

    return Arrays.stream(dayOfWeek.split(","))
        .map(String::trim)
        .filter(token -> !token.isEmpty())
        .map(token -> parseDayOfWeekToken(token, bundleId))
        .flatMap(Optional::stream)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
  }

  private Optional<DayOfWeek> parseDayOfWeekToken(String token, Long bundleId) {
    try {
      return Optional.of(DayOfWeek.valueOf(token.toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      if (bundleId == null) {
        log.warn("Invalid medication day_of_week token skipped: {}", token);
      } else {
        log.warn("Invalid medication day_of_week token skipped. bundleId={}, token={}", bundleId, token);
      }
      return Optional.empty();
    }
  }

  private Double calculateEmotionScoreAverage(
      Map<LocalDate, Double> emotionScoresByDate, LocalDate startDate, LocalDate endDate) {
    List<Double> dailyScores = new ArrayList<>();

    LocalDate currentDate = startDate;
    while (!currentDate.isAfter(endDate)) {
      Double dailyScore = emotionScoresByDate.get(currentDate);
      if (dailyScore != null && dailyScore > 0.0) {
        dailyScores.add(dailyScore);
      }
      currentDate = currentDate.plusDays(1);
    }

    if (dailyScores.isEmpty()) {
      return null;
    }

    double average = dailyScores.stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    return Math.round(average * 100.0) / 100.0;
  }

  /**
   * 감정 데이터를 볼 수 있는 최소 날짜 계산
   * 예: 8월 19일(화) 가입 → 9월 1일(월)부터 변화율 확인 가능
   */
  private LocalDate getMinimumDataDateForEmotion(LocalDate joinDate) {
    // 가입일이 속한 주의 월요일 찾기
    LocalDate firstWeekMonday = joinDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    // 2주차 데이터 필요 → 3주차 월요일부터 확인 가능
    return firstWeekMonday.plusWeeks(2);
  }
}
