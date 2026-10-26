package com.forA.chatbot.medications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.GeneralException;
import com.forA.chatbot.apiPayload.exception.handler.MedicationHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.medications.converter.MedicationConverter;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationItem;
import com.forA.chatbot.medications.domain.MedicationLog;
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
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

  @Mock
  private MedicationBundleRepository medicationBundleRepository;

  @Mock
  private MedicationItemRepository medicationItemRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private MedicationLogRepository medicationLogRepository;

  @Mock
  private MedicationStreakService streakService;

  @Mock
  private MedicationLogSaver medicationLogSaver;

  @Mock
  private MedicationConverter medicationConverter;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private MedicationScheduleOutboxWriter outboxWriter;

  @InjectMocks
  private MedicationService medicationService;

  @Test
  @DisplayName("createMedicationPlan delegates entity and response mapping to converter")
  void createMedicationPlan_usesConverter() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationRequestDto requestDto =
        MedicationRequestDto.builder()
            .name("vitamin")
            .typeTags(List.of("SUPPLEMENT"))
            .takeDays(List.of("MONDAY"))
            .takeTime("09:00")
            .build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    MedicationBundle savedBundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(savedBundle, "id", 10L);
    List<MedicationItem> items = List.of(medicationItem(savedBundle, "SUPPLEMENT"));
    MedicationResponseDto response =
        MedicationResponseDto.builder().id(10L).name("vitamin").build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.existsByUserAndBundleNameAndIsDeletedFalse(user, "vitamin"))
        .willReturn(false);
    given(medicationConverter.toMedicationBundle(eq(user), eq(requestDto), any(LocalTime.class)))
        .willReturn(bundle);
    given(medicationBundleRepository.saveAndFlush(bundle)).willReturn(savedBundle);
    given(medicationConverter.toMedicationItems(savedBundle, requestDto.getTypeTags())).willReturn(items);
    given(medicationItemRepository.saveAll(items)).willReturn(items);
    given(medicationConverter.toMedicationResponse(savedBundle, items)).willReturn(response);

    MedicationResponseDto result = medicationService.createMedicationPlan(1L, requestDto);

    assertThat(result).isSameAs(response);
    verify(medicationConverter).toMedicationBundle(eq(user), eq(requestDto), any(LocalTime.class));
    verify(medicationConverter).toMedicationItems(savedBundle, requestDto.getTypeTags());
    verify(medicationConverter).toMedicationResponse(savedBundle, items);
    verifyPublishedEvent(10L, "CREATED");
  }

  @Test
  @DisplayName("updateMedicationPlan uses converter for day-of-week normalization and response mapping")
  void updateMedicationPlan_usesConverter() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle existingBundle = medicationBundle(user, "old-name");
    ReflectionTestUtils.setField(existingBundle, "id", 20L);
    MedicationUpdateRequestDto requestDto =
        MedicationUpdateRequestDto.builder()
            .name("new-name")
            .takeDays(List.of("monday", "wednesday"))
            .takeTime("09:00")
            .build();
    List<MedicationItem> currentItems = List.of(medicationItem(existingBundle, "SUPPLEMENT"));
    MedicationResponseDto response =
        MedicationResponseDto.builder().id(20L).name("new-name").build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findById(20L)).willReturn(Optional.of(existingBundle));
    given(medicationConverter.toDayOfWeekColumn(requestDto.getTakeDays()))
        .willReturn("MONDAY,WEDNESDAY");
    given(medicationBundleRepository.saveAndFlush(existingBundle)).willReturn(existingBundle);
    given(medicationItemRepository.findByMedicationBundleId(20L)).willReturn(currentItems);
    given(medicationConverter.toMedicationResponse(existingBundle, currentItems)).willReturn(response);

    MedicationResponseDto result = medicationService.updateMedicationPlan(1L, 20L, requestDto);

    assertThat(result).isSameAs(response);
    assertThat(existingBundle.getBundleName()).isEqualTo("new-name");
    assertThat(existingBundle.getDayOfWeek()).isEqualTo("MONDAY,WEDNESDAY");
    verify(medicationConverter).toDayOfWeekColumn(requestDto.getTakeDays());
    verify(medicationConverter).toMedicationResponse(existingBundle, currentItems);
    verifyPublishedEvent(20L, "UPDATED");
  }

  @Test
  @DisplayName("updateMedicationPlan rejects empty takeDays when validation is bypassed")
  void updateMedicationPlan_rejectsEmptyTakeDays() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle existingBundle = medicationBundle(user, "old-name");
    ReflectionTestUtils.setField(existingBundle, "id", 20L);
    MedicationUpdateRequestDto requestDto =
        MedicationUpdateRequestDto.builder().takeDays(List.of()).build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findById(20L)).willReturn(Optional.of(existingBundle));

    assertThatThrownBy(() -> medicationService.updateMedicationPlan(1L, 20L, requestDto))
        .isInstanceOfSatisfying(
            MedicationHandler.class,
            exception -> assertThat(((MedicationHandler) exception).getCode()).isEqualTo(ErrorStatus.MEDICATION_TAKE_DAYS_EMPTY));

    verify(medicationConverter, never()).toDayOfWeekColumn(any());
    verify(medicationBundleRepository, never()).saveAndFlush(any(MedicationBundle.class));
  }

  @Test
  @DisplayName("createLog returns converted existing log without saving duplicate record")
  void createLog_returnsConvertedExistingLog() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 30L);
    MedicationLog existingLog =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(LocalDate.of(2026, 3, 17))
            .isTaken(true)
            .medCondition(1)
            .build();
    MedicationLogRequestDto requestDto =
        MedicationLogRequestDto.builder()
            .medicationId(30L)
            .date("2026-03-17")
            .status("TAKEN")
            .conditionLevel(1)
            .build();
    MedicationLogResponseDto response =
        MedicationLogResponseDto.builder().historyId(99L).message("ok").build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findActiveById(30L)).willReturn(Optional.of(bundle));
    given(medicationLogRepository.findByMedicationBundleAndDate(bundle, LocalDate.of(2026, 3, 17)))
        .willReturn(Optional.of(existingLog));
    given(medicationConverter.toMedicationLogResponse(existingLog)).willReturn(response);

    MedicationLogResponseDto result = medicationService.createLog(1L, requestDto);

    assertThat(result).isSameAs(response);
    verify(medicationConverter).toMedicationLogResponse(existingLog);
    verify(medicationLogRepository, never()).save(any(MedicationLog.class));
    verify(medicationLogSaver, never()).saveNewLog(any(MedicationLog.class));
    verify(streakService, never()).checkAndSendStreakNotification(any(User.class), any(LocalDate.class));
  }

  @Test
  @DisplayName("createLog saves a new log and sends streak notification when no duplicate exists")
  void createLog_newLog_savesAndChecksStreak() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 30L);
    LocalDate targetDate = LocalDate.of(2026, 4, 13);
    MedicationLog savedLog =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(targetDate)
            .isTaken(true)
            .medCondition(2)
            .build();
    MedicationLogRequestDto requestDto =
        MedicationLogRequestDto.builder()
            .medicationId(30L)
            .date("2026-04-13")
            .status("TAKEN")
            .conditionLevel(2)
            .build();
    MedicationLogResponseDto response =
        MedicationLogResponseDto.builder().historyId(71L).message("ok").build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findActiveById(30L)).willReturn(Optional.of(bundle));
    given(medicationLogRepository.findByMedicationBundleAndDate(bundle, targetDate))
        .willReturn(Optional.empty());
    given(medicationLogSaver.saveNewLog(any(MedicationLog.class))).willReturn(savedLog);
    given(medicationConverter.toMedicationLogResponse(savedLog)).willReturn(response);

    MedicationLogResponseDto result = medicationService.createLog(1L, requestDto);

    assertThat(result).isSameAs(response);
    verify(medicationLogSaver).saveNewLog(any(MedicationLog.class));
    verify(streakService).checkAndSendStreakNotification(user, targetDate);
    verify(medicationConverter).toMedicationLogResponse(savedLog);
  }

  @Test
  @DisplayName("createLog returns saved log response even when streak check fails")
  void createLog_streakCheckFailure_returnsSavedLogResponse() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 30L);
    LocalDate targetDate = LocalDate.of(2026, 4, 13);
    MedicationLog savedLog =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(targetDate)
            .isTaken(true)
            .medCondition(2)
            .build();
    MedicationLogRequestDto requestDto =
        MedicationLogRequestDto.builder()
            .medicationId(30L)
            .date("2026-04-13")
            .status("TAKEN")
            .conditionLevel(2)
            .build();
    MedicationLogResponseDto response =
        MedicationLogResponseDto.builder().historyId(71L).message("ok").build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findActiveById(30L)).willReturn(Optional.of(bundle));
    given(medicationLogRepository.findByMedicationBundleAndDate(bundle, targetDate))
        .willReturn(Optional.empty());
    given(medicationLogSaver.saveNewLog(any(MedicationLog.class))).willReturn(savedLog);
    willThrow(new RuntimeException("streak unavailable"))
        .given(streakService)
        .checkAndSendStreakNotification(user, targetDate);
    given(medicationConverter.toMedicationLogResponse(savedLog)).willReturn(response);

    MedicationLogResponseDto result = medicationService.createLog(1L, requestDto);

    assertThat(result).isSameAs(response);
    verify(streakService).checkAndSendStreakNotification(user, targetDate);
    verify(medicationConverter).toMedicationLogResponse(savedLog);
  }

  @Test
  @DisplayName("createLog rejects deleted or missing medication plan")
  void createLog_missingActivePlan_throwsNotFound() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationLogRequestDto requestDto =
        MedicationLogRequestDto.builder()
            .medicationId(30L)
            .date("2026-04-13")
            .status("TAKEN")
            .conditionLevel(2)
            .build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findActiveById(30L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> medicationService.createLog(1L, requestDto))
        .isInstanceOfSatisfying(
            GeneralException.class,
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isEqualTo(ErrorStatus.MEDICATION_PLAN_NOT_FOUND));

    verify(medicationLogRepository, never()).findByMedicationBundleAndDate(any(), any());
    verify(medicationLogSaver, never()).saveNewLog(any(MedicationLog.class));
  }

  @Test
  @DisplayName("createLog rejects calendar-invalid date values")
  void createLog_invalidCalendarDate_throwsInvalidDateFormat() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 30L);
    MedicationLogRequestDto requestDto =
        MedicationLogRequestDto.builder()
            .medicationId(30L)
            .date("2026-99-99")
            .status("TAKEN")
            .conditionLevel(2)
            .build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findActiveById(30L)).willReturn(Optional.of(bundle));

    assertThatThrownBy(() -> medicationService.createLog(1L, requestDto))
        .isInstanceOfSatisfying(
            MedicationHandler.class,
            exception ->
                assertThat(((MedicationHandler) exception).getCode())
                    .isEqualTo(ErrorStatus.MEDICATION_INVALID_DATE_FORMAT));

    verify(medicationLogRepository, never()).findByMedicationBundleAndDate(any(), any());
    verify(medicationLogSaver, never()).saveNewLog(any(MedicationLog.class));
  }

  @Test
  @DisplayName("createLog rejects medication plan owned by another user")
  void createLog_otherUsersPlan_throwsAccessDenied() {
    User requestUser = User.builder().id(1L).email("user@example.com").build();
    User owner = User.builder().id(2L).email("owner@example.com").build();
    MedicationBundle bundle = medicationBundle(owner, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 30L);
    MedicationLogRequestDto requestDto =
        MedicationLogRequestDto.builder()
            .medicationId(30L)
            .date("2026-04-13")
            .status("TAKEN")
            .conditionLevel(2)
            .build();

    given(userRepository.findById(1L)).willReturn(Optional.of(requestUser));
    given(medicationBundleRepository.findActiveById(30L)).willReturn(Optional.of(bundle));

    assertThatThrownBy(() -> medicationService.createLog(1L, requestDto))
        .isInstanceOfSatisfying(
            GeneralException.class,
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isEqualTo(ErrorStatus.MEDICATION_PLAN_ACCESS_DENIED));

    verify(medicationLogRepository, never()).findByMedicationBundleAndDate(any(), any());
    verify(medicationLogSaver, never()).saveNewLog(any(MedicationLog.class));
  }

  @Test
  @DisplayName("duplicate recovery lookup uses a fresh read-only transaction")
  void findExistingLogAfterDuplicate_usesRequiresNewTransaction() throws NoSuchMethodException {
    Method method =
        MedicationLogSaver.class.getMethod(
            "findExistingLogAfterDuplicate", MedicationBundle.class, LocalDate.class);

    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    assertThat(transactional.readOnly()).isTrue();
  }

  @Test
  @DisplayName("createLog returns existing log when duplicate insert wins the race")
  void createLog_duplicateInsertRace_returnsExistingLog() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 30L);
    LocalDate targetDate = LocalDate.of(2026, 4, 13);
    MedicationLog existingLog =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(targetDate)
            .isTaken(true)
            .medCondition(2)
            .build();
    MedicationLogRequestDto requestDto =
        MedicationLogRequestDto.builder()
            .medicationId(30L)
            .date("2026-04-13")
            .status("TAKEN")
            .conditionLevel(2)
            .build();
    MedicationLogResponseDto response =
        MedicationLogResponseDto.builder().historyId(71L).message("ok").build();

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findActiveById(30L)).willReturn(Optional.of(bundle));
    given(medicationLogRepository.findByMedicationBundleAndDate(bundle, targetDate))
        .willReturn(Optional.empty());
    given(medicationLogSaver.saveNewLog(any(MedicationLog.class)))
        .willThrow(new DataIntegrityViolationException("Duplicate entry"));
    given(medicationLogSaver.findExistingLogAfterDuplicate(bundle, targetDate))
        .willReturn(Optional.of(existingLog));
    given(medicationConverter.toMedicationLogResponse(existingLog)).willReturn(response);

    MedicationLogResponseDto result = medicationService.createLog(1L, requestDto);

    assertThat(result).isSameAs(response);
    verify(medicationLogSaver).saveNewLog(any(MedicationLog.class));
    verify(medicationLogSaver).findExistingLogAfterDuplicate(bundle, targetDate);
    verify(medicationConverter).toMedicationLogResponse(existingLog);
    verify(streakService, never()).checkAndSendStreakNotification(any(User.class), any(LocalDate.class));
  }

  @Test
  @DisplayName("getDailyMedications delegates list response mapping to converter")
  void getDailyMedications_usesConverter() {
    LocalDate targetDate = LocalDate.of(2026, 3, 17);
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 40L);
    MedicationLog historyLog =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(targetDate)
            .isTaken(false)
            .build();
    List<MedicationItem> items = List.of(medicationItem(bundle, "SUPPLEMENT"));
    TodayMedicationResponseDto response =
        TodayMedicationResponseDto.builder().medicationId(40L).name("vitamin").build();

    given(
            medicationBundleRepository.findByUserIdAndDayOfWeekContainingAndIsDeletedFalse(
                1L, targetDate.getDayOfWeek().name()))
        .willReturn(List.of(bundle));
    given(medicationLogRepository.findByMedicationBundleAndDate(bundle, targetDate))
        .willReturn(Optional.of(historyLog));
    given(medicationItemRepository.findByMedicationBundleId(40L)).willReturn(items);
    given(
            medicationConverter.toTodayMedicationResponse(
                eq(bundle),
                eq(targetDate),
                eq(items),
                eq(historyLog),
                any(LocalTime.class)))
        .willReturn(response);

    List<TodayMedicationResponseDto> result =
        medicationService.getDailyMedications(1L, targetDate);

    assertThat(result).containsExactly(response);
    verify(medicationConverter)
        .toTodayMedicationResponse(
            eq(bundle),
            eq(targetDate),
            eq(items),
            eq(historyLog),
            any(LocalTime.class));
  }

  @Test
  @DisplayName("getDailyMedications returns plans sorted by local take time ascending")
  void getDailyMedications_sortsByLocalTakeTimeAscending() {
    LocalDate targetDate = LocalDate.of(2026, 3, 17);
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle afternoonBundle = medicationBundle(user, "afternoon");
    MedicationBundle noonBundle = medicationBundle(user, "noon");
    afternoonBundle.updateMedicationPlan("afternoon", "TUESDAY", LocalTime.of(4, 50));
    noonBundle.updateMedicationPlan("noon", "TUESDAY", LocalTime.of(3, 0));
    ReflectionTestUtils.setField(afternoonBundle, "id", 41L);
    ReflectionTestUtils.setField(noonBundle, "id", 42L);
    List<MedicationItem> afternoonItems = List.of(medicationItem(afternoonBundle, "SUPPLEMENT"));
    List<MedicationItem> noonItems = List.of(medicationItem(noonBundle, "SUPPLEMENT"));
    TodayMedicationResponseDto afternoonResponse =
        TodayMedicationResponseDto.builder()
            .medicationId(41L)
            .name("afternoon")
            .takeTime(LocalTime.of(13, 50))
            .build();
    TodayMedicationResponseDto noonResponse =
        TodayMedicationResponseDto.builder()
            .medicationId(42L)
            .name("noon")
            .takeTime(LocalTime.NOON)
            .build();

    given(
            medicationBundleRepository.findByUserIdAndDayOfWeekContainingAndIsDeletedFalse(
                1L, targetDate.getDayOfWeek().name()))
        .willReturn(List.of(afternoonBundle, noonBundle));
    given(medicationLogRepository.findByMedicationBundleAndDate(afternoonBundle, targetDate))
        .willReturn(Optional.empty());
    given(medicationLogRepository.findByMedicationBundleAndDate(noonBundle, targetDate))
        .willReturn(Optional.empty());
    given(medicationItemRepository.findByMedicationBundleId(41L)).willReturn(afternoonItems);
    given(medicationItemRepository.findByMedicationBundleId(42L)).willReturn(noonItems);
    given(
            medicationConverter.toTodayMedicationResponse(
                eq(afternoonBundle),
                eq(targetDate),
                eq(afternoonItems),
                eq(null),
                eq(LocalTime.of(13, 50))))
        .willReturn(afternoonResponse);
    given(
            medicationConverter.toTodayMedicationResponse(
                eq(noonBundle),
                eq(targetDate),
                eq(noonItems),
                eq(null),
                eq(LocalTime.NOON)))
        .willReturn(noonResponse);

    List<TodayMedicationResponseDto> result =
        medicationService.getDailyMedications(1L, targetDate);

    assertThat(result).containsExactly(noonResponse, afternoonResponse);
  }

  @Test
  @DisplayName("deleteMedicationPlan publishes deleted event after soft delete")
  void deleteMedicationPlan_publishesDeletedEvent() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationBundle bundle = medicationBundle(user, "vitamin");
    ReflectionTestUtils.setField(bundle, "id", 50L);

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(medicationBundleRepository.findActiveById(50L)).willReturn(Optional.of(bundle));
    given(medicationBundleRepository.saveAndFlush(bundle)).willReturn(bundle);

    medicationService.deleteMedicationPlan(1L, 50L);

    verify(medicationBundleRepository).saveAndFlush(bundle);
    verifyPublishedEvent(50L, "DELETED");
  }

  private void verifyPublishedEvent(Long bundleId, String expectedChangeTypeName) {
    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    assertThat(eventCaptor.getValue())
        .isInstanceOf(com.forA.chatbot.medications.event.MedicationBundleChangedEvent.class);

    com.forA.chatbot.medications.event.MedicationBundleChangedEvent event =
        (com.forA.chatbot.medications.event.MedicationBundleChangedEvent) eventCaptor.getValue();

    assertThat(event.getBundleId()).isEqualTo(bundleId);
    assertThat(event.getChangeType().name()).isEqualTo(expectedChangeTypeName);
  }

  private MedicationBundle medicationBundle(User user, String name) {
    return MedicationBundle.builder()
        .user(user)
        .bundleName(name)
        .dayOfWeek("MONDAY")
        .scheduledTime(LocalTime.of(0, 0))
        .build();
  }

  private MedicationItem medicationItem(MedicationBundle bundle, String typeTag) {
    return MedicationItem.builder()
        .medicationBundle(bundle)
        .medicationName(typeTag)
        .build();
  }
}
