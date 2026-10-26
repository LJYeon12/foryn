package com.forA.chatbot.medications.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.GeneralException;
import com.forA.chatbot.apiPayload.exception.handler.MedicationHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.medications.converter.MedicationConverter;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationItem;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.event.MedicationBundleChangedEvent;
import com.forA.chatbot.medications.event.MedicationBundleChangedEvent.ChangeType;
import com.forA.chatbot.medications.dto.MedicationLogRequestDto;
import com.forA.chatbot.medications.dto.MedicationLogResponseDto;
import com.forA.chatbot.medications.dto.MedicationRequestDto;
import com.forA.chatbot.medications.dto.MedicationResponseDto;
import com.forA.chatbot.medications.dto.MedicationUpdateRequestDto;
import com.forA.chatbot.medications.dto.TodayMedicationResponseDto;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationItemRepository;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MedicationService {

  private final MedicationBundleRepository medicationBundleRepository;
  private final MedicationItemRepository medicationItemRepository;
  private final UserRepository userRepository;
  private final MedicationLogRepository medicationLogRepository;
  private final MedicationLogSaver medicationLogSaver;
  private final MedicationStreakService streakService;
  private final MedicationConverter medicationConverter;
  private final ApplicationEventPublisher eventPublisher;
  private final MedicationScheduleOutboxWriter outboxWriter;

  public MedicationResponseDto createMedicationPlan(Long userId, MedicationRequestDto requestDto) {
    log.info("약 복용 계획 생성 시작 - 사용자 ID: {}, 약 이름: {}", userId, requestDto.getName());

    // 사용자 조회
    User user = findUserById(userId);

    // 같은 이름의 약 복용 계획 이미 존재하는지 중복 체크
    validateDuplicateName(user, requestDto.getName());

    // 복용 계획 시간
    LocalTime scheduledTime = parseLocalTime(requestDto.getTakeTime());
    // KST -> UTC 변환
    LocalTime utcTime = convertToUtc(scheduledTime);

    MedicationBundle medicationBundle =
        medicationConverter.toMedicationBundle(user, requestDto, utcTime);

    MedicationBundle savedBundle = medicationBundleRepository.saveAndFlush(medicationBundle);

    List<MedicationItem> medicationItems =
        medicationConverter.toMedicationItems(savedBundle, requestDto.getTypeTags());
    List<MedicationItem> savedItems = medicationItemRepository.saveAll(medicationItems);

    log.info("약 복용 계획 생성 완료 - ID: {}", savedBundle.getId());

    outboxWriter.recordChange(savedBundle);
    eventPublisher.publishEvent(
        new MedicationBundleChangedEvent(savedBundle.getId(), ChangeType.CREATED));

    return medicationConverter.toMedicationResponse(savedBundle, savedItems);
  }

  public MedicationResponseDto updateMedicationPlan(
      Long userId, Long planId, MedicationUpdateRequestDto requestDto) {
    log.info("약 복용 계획 수정 시작 - 사용자 ID: {}, 계획 ID: {}", userId, planId);

    // 1. 사용자 조회
    User user = findUserById(userId);

    // 2. 기존 MedicationBundle 조회 및 권한 확인
    MedicationBundle existingBundle =
        medicationBundleRepository
            .findById(planId)
            .orElseThrow(() -> new GeneralException(ErrorStatus.MEDICATION_PLAN_NOT_FOUND));

    // 사용자 권한 확인
    if (!existingBundle.getUser().getId().equals(userId)) {
      log.warn("권한 없는 접근 시도 - 사용자 ID: {}, 계획 ID: {}", userId, planId);
      throw new GeneralException(ErrorStatus.MEDICATION_PLAN_ACCESS_DENIED);
    }

    // 3. 필드별 부분 업데이트
    String updatedBundleName =
        requestDto.getName() != null ? requestDto.getName() : existingBundle.getBundleName();

    String updatedDayOfWeek =
        requestDto.getTakeDays() != null
            ? toValidatedDayOfWeekColumn(requestDto.getTakeDays())
            : existingBundle.getDayOfWeek();

    LocalTime updatedScheduledTime = requestDto.getTakeTime() != null
        ? convertToUtc(parseLocalTime(requestDto.getTakeTime()))
        : existingBundle.getScheduledTime();


    // 4. MedicationBundle 업데이트
    existingBundle.updateMedicationPlan(
        updatedBundleName,
        updatedDayOfWeek,
        updatedScheduledTime);

    MedicationBundle updatedBundle = medicationBundleRepository.saveAndFlush(existingBundle);
    log.info("MedicationBundle 업데이트 완료 - ID: {}", updatedBundle.getId());

    // 5. typeTags가 제공된 경우에만 MedicationItem 업데이트
    if (requestDto.getTypeTags() != null) {
      // 기존 MedicationItem들 삭제
      List<MedicationItem> existingItems =
          medicationItemRepository.findByMedicationBundleId(planId);
      if (!existingItems.isEmpty()) {
        medicationItemRepository.deleteAll(existingItems);
        log.info("기존 MedicationItem {} 개 삭제 완료", existingItems.size());
      }

      // 새로운 MedicationItem들 생성 및 저장
      if (!requestDto.getTypeTags().isEmpty()) {
        List<MedicationItem> newMedicationItems =
            medicationConverter.toMedicationItems(updatedBundle, requestDto.getTypeTags());
        medicationItemRepository.saveAll(newMedicationItems);
        log.info("새로운 MedicationItem {} 개 저장 완료", newMedicationItems.size());
      }
    }

    List<MedicationItem> currentItems = medicationItemRepository.findByMedicationBundleId(planId);

    log.info("약 복용 계획 수정 완료 - ID: {}", updatedBundle.getId());

    outboxWriter.recordChange(updatedBundle);
    eventPublisher.publishEvent(
        new MedicationBundleChangedEvent(updatedBundle.getId(), ChangeType.UPDATED));

    return medicationConverter.toMedicationResponse(updatedBundle, currentItems);
  }

  public MedicationLogResponseDto createLog(Long userId, MedicationLogRequestDto requestDto) {
    log.info("약 복용 기록 생성 시작 - 사용자 ID: {}, medicationId: {}", userId, requestDto.getMedicationId());

    User user = findUserById(userId);
    // 약 번들 조회
    MedicationBundle bundle =
        medicationBundleRepository
            .findActiveById(requestDto.getMedicationId())
            .orElseThrow(
                () -> {
                  log.error("MedicationBundle을 찾을 수 없음 - ID: {}", requestDto.getMedicationId());
                  return new GeneralException(ErrorStatus.MEDICATION_PLAN_NOT_FOUND);
                });

    if (!bundle.getUser().getId().equals(userId)) {
      log.warn("복용 계획 접근 권한 없음 - userId: {}, medicationId: {}", userId, requestDto.getMedicationId());
      throw new GeneralException(ErrorStatus.MEDICATION_PLAN_ACCESS_DENIED);
    }

    // 상태 검증
    boolean isTaken = "TAKEN".equalsIgnoreCase(requestDto.getStatus());
    if (isTaken && requestDto.getConditionLevel() == null) {
      throw new GeneralException(ErrorStatus.MEDICATION_CONDITION_REQUIRED);
    }

    // 날짜 변환
    LocalDate date = parseLocalDate(requestDto.getDate());

    // 해당 날짜에 이미 기록이 있는지 확인 (중복 체크)
    Optional<MedicationLog> existingLog = medicationLogRepository.findByMedicationBundleAndDate(bundle, date);

    if (existingLog.isPresent()) {
      log.warn("이미 해당 날짜에 복용 기록이 존재함 - bundleId: {}, date: {}", bundle.getId(), date);

      // 에러 대신 기존 기록을 그냥 반환 (이미 처리됐으므로 성공으로 간주하는 '멱등성' 처리)
      return medicationConverter.toMedicationLogResponse(existingLog.get());
    }

    // MedicationLog 생성 및 저장
    MedicationLog logEntity =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(date)
            .isTaken(isTaken)
            .medCondition(requestDto.getConditionLevel())
            .build();

    try {
      MedicationLog saved = medicationLogSaver.saveNewLog(logEntity);
      log.info("MedicationLog 저장 완료 - historyId: {}", saved.getId());
      // 3일 연속 복용 달성 확인
      checkStreakNotificationSafely(user, bundle, date);
      return medicationConverter.toMedicationLogResponse(saved);
    } catch (DataIntegrityViolationException e) {
      log.warn(
          "MedicationLog duplicate race detected - bundleId: {}, date: {}",
          bundle.getId(),
          date);

      MedicationLog existingAfterRace =
          medicationLogSaver
              .findExistingLogAfterDuplicate(bundle, date)
              .orElseThrow(() -> e);

      return medicationConverter.toMedicationLogResponse(existingAfterRace);
    }
  }

  private void checkStreakNotificationSafely(User user, MedicationBundle bundle, LocalDate date) {
    try {
      streakService.checkAndSendStreakNotification(user, date);
    } catch (Exception e) {
      log.error(
          "Medication streak check failed after log save - userId: {}, bundleId: {}, date: {}",
          user.getId(),
          bundle.getId(),
          date,
          e);
    }
  }

  @Transactional
  public void deleteMedicationPlan(Long userId, Long planId) {
    // 사용자 조회
    User user = findUserById(userId);

    // 활성 계획 조회 및 권한 확인
    MedicationBundle existingBundle =
        medicationBundleRepository
            .findActiveById(planId)
            .orElseThrow(() -> new GeneralException(ErrorStatus.MEDICATION_PLAN_NOT_FOUND));

    // 사용자 권한 확인
    if (!existingBundle.getUser().getId().equals(userId)) {
      throw new GeneralException(ErrorStatus.MEDICATION_PLAN_ACCESS_DENIED);
    }

    // 소프트 삭제 수행
    existingBundle.delete();
    medicationBundleRepository.saveAndFlush(existingBundle);

    outboxWriter.recordChange(existingBundle);
    eventPublisher.publishEvent(
        new MedicationBundleChangedEvent(existingBundle.getId(), ChangeType.DELETED));
  }

  public List<TodayMedicationResponseDto> getDailyMedications(Long userId, LocalDate date) {
    // 파라미터가 없으면 오늘 날짜로 설정
    LocalDate searchDate = (date == null) ? LocalDate.now() : date;
    log.info("복약 계획 조회 요청 - 사용자 ID: {}, 날짜: {}", userId, searchDate);

    // 요일 계산
    String targetDayOfWeek = searchDate.getDayOfWeek().name();
    log.info("조회 요일: {}", targetDayOfWeek);

    // 해당 요일의 복약 계획들 조회
    List<MedicationBundle> bundles =
        medicationBundleRepository.findByUserIdAndDayOfWeekContainingAndIsDeletedFalse(userId, targetDayOfWeek);

    log.info("조회된 복약 계획 수: {}", bundles.size());
    // TODO: N+1 문제 발생 지점. 추후 IN절 조회나 일괄 조회로 최적화 예정
    // 각 복약 계획에 대해 해당 날짜의 기록 조회 및 응답 생성
    List<TodayMedicationResponseDto> responses =
        bundles.stream()
            .map(
                bundle -> {
                  Optional<MedicationLog> historyLog =
                      medicationLogRepository.findByMedicationBundleAndDate(bundle, searchDate);
                  List<MedicationItem> items =
                      medicationItemRepository.findByMedicationBundleId(bundle.getId());

                  return medicationConverter.toTodayMedicationResponse(
                      bundle,
                      searchDate,
                      items,
                      historyLog.orElse(null),
                      convertToLocal(bundle.getScheduledTime()));
                })
            .sorted(Comparator.comparing(TodayMedicationResponseDto::getTakeTime))
            .collect(Collectors.toList());

    log.info("복약 계획 조회 완료 - 사용자 ID: {}, 날짜: {}, 계획 수: {}", userId, searchDate, responses.size());

    return responses;
  }

  private User findUserById(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> {
              log.error("사용자를 찾을 수 없습니다 - ID: {}", userId);
              return new GeneralException(ErrorStatus.USER_NOT_FOUND);
            });
  }

  private void validateDuplicateName(User user, String name) {
    if (medicationBundleRepository.existsByUserAndBundleNameAndIsDeletedFalse(user, name)) {
      throw new MedicationHandler(ErrorStatus.MEDICATION_PLAN_ALREADY_EXISTS);
    }
  }

  private String toValidatedDayOfWeekColumn(List<String> takeDays) {
    if (takeDays.isEmpty()) {
      throw new MedicationHandler(ErrorStatus.MEDICATION_TAKE_DAYS_EMPTY);
    }
    return medicationConverter.toDayOfWeekColumn(takeDays);
  }

  private LocalTime parseLocalTime(String timeStr) {
    try {
      return LocalTime.parse(timeStr);
    } catch (DateTimeParseException | NullPointerException e) {
      throw new MedicationHandler(ErrorStatus.MEDICATION_INVALID_TIME_FORMAT);
    }
  }

  private static final String DEFAULT_ZONE_ID = "Asia/Seoul"; // 임시 고정

  private LocalDate parseLocalDate(String dateStr) {
    try {
      return LocalDate.parse(dateStr);
    } catch (DateTimeParseException | NullPointerException e) {
      throw new MedicationHandler(ErrorStatus.MEDICATION_INVALID_DATE_FORMAT);
    }
  }

  private LocalTime convertToUtc(LocalTime localTime) {
    return ZonedDateTime.of(LocalDate.now(), localTime, ZoneId.of(DEFAULT_ZONE_ID))
        .withZoneSameInstant(ZoneId.of("UTC"))
        .toLocalTime();
  }

  private LocalTime convertToLocal(LocalTime utcTime) {
    return ZonedDateTime.of(LocalDate.now(), utcTime, ZoneId.of("UTC"))
        .withZoneSameInstant(ZoneId.of(DEFAULT_ZONE_ID))
        .toLocalTime();
  }
}
