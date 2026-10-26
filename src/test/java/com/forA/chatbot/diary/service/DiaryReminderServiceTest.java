package com.forA.chatbot.diary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.DiaryHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.diary.domain.DiaryReminderRescheduleJob;
import com.forA.chatbot.diary.event.DiaryReminderRescheduleRequestedEvent;
import com.forA.chatbot.diary.dto.DiaryReminderUpdateRequest;
import com.forA.chatbot.diary.dto.DiaryReminderUpdateResponse;
import com.forA.chatbot.diary.repository.DiaryReminderRepository;
import com.forA.chatbot.diary.repository.DiaryReminderRescheduleJobRepository;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DiaryReminderServiceTest {

  @InjectMocks
  private DiaryReminderService diaryReminderService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private DiaryReminderRepository diaryReminderRepository;

  @Mock
  private DiaryReminderRescheduleJobRepository diaryReminderRescheduleJobRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Test
  @DisplayName("알림 설정 전체 교체 시 기존 알림 ID를 가진 재예약 job을 함께 저장한다")
  void updateDiaryReminders_replacesRemindersAndCreatesRescheduleJob() {
    User user = User.builder().id(1L).email("reminder@example.com").timeZone("Asia/Seoul").build();
    DiaryReminder oldReminder = DiaryReminder.builder()
        .user(user)
        .reminderTime(LocalTime.of(21, 0))
        .enabled(true)
        .build();
    org.springframework.test.util.ReflectionTestUtils.setField(oldReminder, "id", 10L);
    DiaryReminderUpdateRequest request = new DiaryReminderUpdateRequest(List.of(
        new DiaryReminderUpdateRequest.Reminder("20:00", true),
        new DiaryReminderUpdateRequest.Reminder("22:00", false)));

    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(1L))
        .willReturn(List.of(oldReminder));
    given(diaryReminderRescheduleJobRepository.save(any(DiaryReminderRescheduleJob.class)))
        .willAnswer(invocation -> {
          DiaryReminderRescheduleJob job = invocation.getArgument(0);
          org.springframework.test.util.ReflectionTestUtils.setField(job, "id", 50L);
          return job;
        });

    DiaryReminderUpdateResponse response = diaryReminderService.updateDiaryReminders(1L, request);

    assertThat(response.maxCount()).isEqualTo(3);
    assertThat(response.reminders()).extracting(DiaryReminderUpdateResponse.Reminder::time)
        .containsExactly("20:00", "22:00");
    assertThat(response.reminders()).extracting(DiaryReminderUpdateResponse.Reminder::enabled)
        .containsExactly(true, false);
    verify(diaryReminderRepository).deleteByUserId(1L);

    ArgumentCaptor<List<DiaryReminder>> remindersCaptor = ArgumentCaptor.forClass(List.class);
    verify(diaryReminderRepository).saveAll(remindersCaptor.capture());
    assertThat(remindersCaptor.getValue()).extracting(DiaryReminder::getReminderTime)
        .containsExactly(LocalTime.of(20, 0), LocalTime.of(22, 0));

    ArgumentCaptor<DiaryReminderRescheduleJob> jobCaptor =
        ArgumentCaptor.forClass(DiaryReminderRescheduleJob.class);
    verify(diaryReminderRescheduleJobRepository).save(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getOldReminderIds()).containsExactly(10L);
    assertThat(jobCaptor.getValue().getScheduledDate())
        .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
    ArgumentCaptor<DiaryReminderRescheduleRequestedEvent> eventCaptor =
        ArgumentCaptor.forClass(DiaryReminderRescheduleRequestedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().jobId()).isEqualTo(50L);
  }

  @Test
  @DisplayName("빈 알림 목록은 기존 설정을 모두 삭제하고 재예약 job을 저장한다")
  void updateDiaryReminders_allowsEmptyList() {
    User user = User.builder().id(1L).email("reminder@example.com").build();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(1L)).willReturn(List.of());
    given(diaryReminderRescheduleJobRepository.save(any(DiaryReminderRescheduleJob.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    DiaryReminderUpdateResponse response = diaryReminderService.updateDiaryReminders(
        1L, new DiaryReminderUpdateRequest(List.of()));

    assertThat(response.reminders()).isEmpty();
    verify(diaryReminderRepository).deleteByUserId(1L);
    verify(diaryReminderRepository, never()).saveAll(any());
    verify(diaryReminderRescheduleJobRepository).save(any(DiaryReminderRescheduleJob.class));
  }

  @Test
  @DisplayName("알림이 네 개면 최대 개수 오류를 반환한다")
  void updateDiaryReminders_rejectsMoreThanThree() {
    DiaryReminderUpdateRequest request = new DiaryReminderUpdateRequest(List.of(
        new DiaryReminderUpdateRequest.Reminder("09:00", true),
        new DiaryReminderUpdateRequest.Reminder("12:00", true),
        new DiaryReminderUpdateRequest.Reminder("18:00", true),
        new DiaryReminderUpdateRequest.Reminder("21:00", true)));

    assertThatThrownBy(() -> diaryReminderService.updateDiaryReminders(1L, request))
        .isInstanceOf(DiaryHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.DIARY_REMINDER_LIMIT_EXCEEDED);

    verifyNoInteractions(userRepository, diaryReminderRepository, diaryReminderRescheduleJobRepository);
  }

  @Test
  @DisplayName("같은 시간의 알림을 중복 설정하면 중복 시간 오류를 반환한다")
  void updateDiaryReminders_rejectsDuplicatedTime() {
    DiaryReminderUpdateRequest request = new DiaryReminderUpdateRequest(List.of(
        new DiaryReminderUpdateRequest.Reminder("21:00", true),
        new DiaryReminderUpdateRequest.Reminder("21:00", false)));

    assertThatThrownBy(() -> diaryReminderService.updateDiaryReminders(1L, request))
        .isInstanceOf(DiaryHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.DIARY_REMINDER_TIME_DUPLICATED);

    verifyNoInteractions(userRepository, diaryReminderRepository, diaryReminderRescheduleJobRepository);
  }

  @Test
  @DisplayName("빈 알림 시간은 필수 시간 오류를 반환한다")
  void updateDiaryReminders_rejectsBlankTime() {
    DiaryReminderUpdateRequest request = new DiaryReminderUpdateRequest(
        List.of(new DiaryReminderUpdateRequest.Reminder(" ", true)));

    assertThatThrownBy(() -> diaryReminderService.updateDiaryReminders(1L, request))
        .isInstanceOf(DiaryHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.DIARY_REMINDER_TIME_REQUIRED);
  }

  @Test
  @DisplayName("HH:mm 형식이 아닌 알림 시간은 형식 오류를 반환한다")
  void updateDiaryReminders_rejectsInvalidTimeFormat() {
    DiaryReminderUpdateRequest request = new DiaryReminderUpdateRequest(
        List.of(new DiaryReminderUpdateRequest.Reminder("9:00", true)));

    assertThatThrownBy(() -> diaryReminderService.updateDiaryReminders(1L, request))
        .isInstanceOf(DiaryHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.DIARY_REMINDER_TIME_INVALID);
  }

  @Test
  @DisplayName("알림 활성화 여부가 없으면 필수 여부 오류를 반환한다")
  void updateDiaryReminders_rejectsNullEnabled() {
    DiaryReminderUpdateRequest request = new DiaryReminderUpdateRequest(
        List.of(new DiaryReminderUpdateRequest.Reminder("21:00", null)));

    assertThatThrownBy(() -> diaryReminderService.updateDiaryReminders(1L, request))
        .isInstanceOf(DiaryHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.DIARY_REMINDER_ENABLED_REQUIRED);
  }
}
