package com.forA.chatbot.diary.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.DiaryHandler;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.diary.domain.DiaryReminderRescheduleJob;
import com.forA.chatbot.diary.domain.enums.DiaryReminderRescheduleJobStatus;
import com.forA.chatbot.diary.dto.DiaryReminderUpdateRequest;
import com.forA.chatbot.diary.dto.DiaryReminderUpdateResponse;
import com.forA.chatbot.diary.event.DiaryReminderRescheduleRequestedEvent;
import com.forA.chatbot.diary.repository.DiaryReminderRepository;
import com.forA.chatbot.diary.repository.DiaryReminderRescheduleJobRepository;
import com.forA.chatbot.user.domain.User;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryReminderService {

  private static final int MAX_REMINDER_COUNT = 3;
  private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");
  private static final Pattern REMINDER_TIME_PATTERN =
      Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

  private final UserRepository userRepository;
  private final DiaryReminderRepository diaryReminderRepository;
  private final DiaryReminderRescheduleJobRepository diaryReminderRescheduleJobRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public DiaryReminderUpdateResponse updateDiaryReminders(
      Long userId, DiaryReminderUpdateRequest request) {
    List<ReminderCommand> commands = validateAndParse(request);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    LocalDate scheduledDate = LocalDate.now(zoneIdOf(user));

    // 전체 교체 전에 기존 알림 ID를 보관해야 오늘 남은 Cloud Tasks 삭제 작업을 만들 수 있다.
    List<Long> oldReminderIds = diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(userId)
        .stream()
        .map(DiaryReminder::getId)
        .toList();

    diaryReminderRepository.deleteByUserId(userId);
    if (!commands.isEmpty()) {
      diaryReminderRepository.saveAll(toEntities(user, commands));
    }

    // API 트랜잭션에는 작업만 남기고, 커밋 이후 리스너가 Cloud Tasks 재예약을 처리한다.
    DiaryReminderRescheduleJob job = diaryReminderRescheduleJobRepository.save(
        DiaryReminderRescheduleJob.builder()
        .user(user)
        .scheduledDate(scheduledDate)
        .oldReminderIds(oldReminderIds)
        .status(DiaryReminderRescheduleJobStatus.PENDING)
        .build());
    eventPublisher.publishEvent(new DiaryReminderRescheduleRequestedEvent(job.getId()));

    log.info("한줄일기 알림 설정 저장 완료 - userId={}, count={}", userId, commands.size());
    return new DiaryReminderUpdateResponse(toResponseItems(commands), MAX_REMINDER_COUNT);
  }

  private List<ReminderCommand> validateAndParse(DiaryReminderUpdateRequest request) {
    List<DiaryReminderUpdateRequest.Reminder> reminders =
        request == null || request.reminders() == null ? List.of() : request.reminders();
    if (reminders.size() > MAX_REMINDER_COUNT) {
      throw new DiaryHandler(ErrorStatus.DIARY_REMINDER_LIMIT_EXCEEDED);
    }

    Set<LocalTime> uniqueTimes = new HashSet<>();
    return reminders.stream()
        .map(reminder -> validateAndParseReminder(reminder, uniqueTimes))
        .toList();
  }

  private ReminderCommand validateAndParseReminder(
      DiaryReminderUpdateRequest.Reminder reminder, Set<LocalTime> uniqueTimes) {
    if (reminder == null || reminder.time() == null || reminder.time().isBlank()) {
      throw new DiaryHandler(ErrorStatus.DIARY_REMINDER_TIME_REQUIRED);
    }
    if (reminder.enabled() == null) {
      throw new DiaryHandler(ErrorStatus.DIARY_REMINDER_ENABLED_REQUIRED);
    }
    if (!REMINDER_TIME_PATTERN.matcher(reminder.time()).matches()) {
      throw new DiaryHandler(ErrorStatus.DIARY_REMINDER_TIME_INVALID);
    }

    LocalTime reminderTime = LocalTime.parse(reminder.time());
    if (!uniqueTimes.add(reminderTime)) {
      throw new DiaryHandler(ErrorStatus.DIARY_REMINDER_TIME_DUPLICATED);
    }
    return new ReminderCommand(reminderTime, reminder.enabled());
  }

  private List<DiaryReminder> toEntities(User user, List<ReminderCommand> commands) {
    return commands.stream()
        .map(command -> DiaryReminder.builder()
            .user(user)
            .reminderTime(command.time())
            .enabled(command.enabled())
            .build())
        .toList();
  }

  private List<DiaryReminderUpdateResponse.Reminder> toResponseItems(
      List<ReminderCommand> commands) {
    return commands.stream()
        .map(command -> new DiaryReminderUpdateResponse.Reminder(
            command.time().toString(),
            command.enabled()))
        .toList();
  }

  private ZoneId zoneIdOf(User user) {
    try {
      return ZoneId.of(user.getTimeZone());
    } catch (DateTimeException | NullPointerException e) {
      log.warn("사용자 timeZone이 유효하지 않아 기본 시간대를 사용합니다. userId={}", user.getId());
      return DEFAULT_ZONE_ID;
    }
  }

  private record ReminderCommand(
      LocalTime time,
      boolean enabled
  ) {
  }
}
