package com.forA.chatbot.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.repository.RefreshTokenRepository;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.auth.service.BlacklistService;
import com.forA.chatbot.chat.service.ChatService;
import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.diary.repository.DiaryReminderRepository;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.converter.UserConverter;
import com.forA.chatbot.user.converter.UserProfileRequestConverter;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import com.forA.chatbot.user.dto.UserProfileResponse;
import com.forA.chatbot.user.dto.UserProfileUpdateRequest;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @InjectMocks
  private UserService userService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private MedicationLogRepository medicationLogRepository;

  @Mock
  private MedicationBundleRepository medicationBundleRepository;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private BlacklistService blacklistService;

  @Mock
  private ChatService chatService;

  @Mock
  private DeviceTokenRepository deviceTokenRepository;

  @Mock
  private DiaryReminderRepository diaryReminderRepository;

  @Mock
  private UserConverter userConverter;

  @Mock
  private UserProfileRequestConverter userProfileRequestConverter;
  @Mock private jakarta.persistence.EntityManager entityManager;

  @BeforeEach
  void injectPersistenceContext() {
    ReflectionTestUtils.setField(userService, "entityManager", entityManager);
  }

  @Test
  @DisplayName("deactivateUser keeps related data while soft deleting the user")
  void deactivateUser_Success() {
    Long userId = 1L;
    User user = User.builder().id(userId).email("test@example.com").build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    userService.deactivateUser(userId);

    assertThat(user.isDeleted()).isTrue();

    verify(refreshTokenRepository, times(1)).deleteByUserId(userId);
    verify(userRepository, never()).delete(any(User.class));
    verify(medicationLogRepository, never()).deleteByUserId(anyLong());
    verify(medicationBundleRepository, never()).deleteByUserId(anyLong());
    verify(chatService, never()).deleteAllChatDataByUserId(anyLong());
    verify(deviceTokenRepository, times(1)).deleteAllByUserId(userId);
  }

  @Test
  @DisplayName("deactivateUser throws when the user does not exist")
  void deactivateUser_UserNotFound() {
    Long userId = 999L;
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.deactivateUser(userId))
        .isInstanceOf(UserHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("resetUserData clears chat and medication data while resetting profile fields")
  void shouldClearAllUserDataWhenResetInfo() {
    Long userId = 1L;
    User user =
        User.builder()
            .id(userId)
            .email("test@example.com")
            .lastName("Hong")
            .firstName("Gildong")
            .fullName("Hong Gildong")
            .birthYear(1990)
            .gender(com.forA.chatbot.enums.Gender.MALE)
            .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    userService.resetUserData(userId);

    assertThat(user.getBirthYear()).isNull();
    assertThat(user.getGender()).isEqualTo(com.forA.chatbot.enums.Gender.UNKNOWN);
    assertThat(user.getFullName()).isNotNull();
    assertThat(user.getFirstName()).isNotNull();
    assertThat(user.getLastName()).isNotNull();
    assertThat(user.getJobs()).isEmpty();
    assertThat(user.getDisorders()).isEmpty();
    assertThat(user.getSymptoms()).isEmpty();

    verify(chatService, times(1)).deleteAllChatDataByUserId(userId);
    verify(medicationBundleRepository, times(1)).deleteByUserId(userId);
    verify(medicationLogRepository, times(1)).deleteByUserId(userId);
  }

  @Test
  @DisplayName("updateUserProfile returns current diary reminders")
  void updateUserProfile_usesRequestConverter() {
    Long userId = 1L;
    User user = User.builder().id(userId).email("test@example.com").fullName("Hong Gildong").build();
    UserProfileUpdateRequest request = new UserProfileUpdateRequest();
    Set<JobType> jobs = Set.of(JobType.OFFICE_WORKER);
    Set<DisorderType> disorders = Set.of(DisorderType.DEPRESSION);
    Set<SymptomType> symptoms = Set.of(SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES);

    ReflectionTestUtils.setField(request, "nickname", "mori");
    ReflectionTestUtils.setField(request, "jobs", List.of(JobType.OFFICE_WORKER.getName()));
    ReflectionTestUtils.setField(request, "disorders", List.of(DisorderType.DEPRESSION.getName()));
    ReflectionTestUtils.setField(
        request,
        "symptoms",
        Map.of(
            DisorderType.DEPRESSION.getName(),
            List.of(SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription())));
    List<DiaryReminder> reminders =
        List.of(DiaryReminder.builder().user(user).reminderTime(LocalTime.of(21, 0)).enabled(true).build());
    UserProfileResponse response =
        new UserProfileResponse(
            userId,
            "mori",
            null,
            null,
            List.of(JobType.OFFICE_WORKER.getName()),
            List.of(DisorderType.DEPRESSION.getName()),
            Map.of(),
            "Asia/Seoul",
            List.of(new UserProfileResponse.DiaryReminder("21:00", true)),
            3);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userProfileRequestConverter.convertJobs(request.getJobs())).willReturn(jobs);
    given(userProfileRequestConverter.convertDisorders(request.getDisorders())).willReturn(disorders);
    given(userProfileRequestConverter.convertSymptoms(request.getSymptoms())).willReturn(symptoms);
    given(diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(userId)).willReturn(reminders);
    given(userConverter.toUserProfileResponse(user, reminders)).willReturn(response);

    UserProfileResponse result = userService.updateUserProfile(userId, request);

    assertThat(result.getDiaryReminders())
        .extracting(UserProfileResponse.DiaryReminder::time)
        .containsExactly("21:00");
    verify(userProfileRequestConverter).convertJobs(request.getJobs());
    verify(userProfileRequestConverter).convertDisorders(request.getDisorders());
    verify(userProfileRequestConverter).convertSymptoms(request.getSymptoms());
    verify(diaryReminderRepository).findByUserIdOrderByReminderTimeAsc(userId);
    verify(userConverter).toUserProfileResponse(user, reminders);
    verify(userRepository).save(user);
  }

  @Test
  @DisplayName("getUserProfile returns timezone and diary reminders")
  void getUserProfile_includesDiaryReminders() {
    Long userId = 1L;
    User user =
        User.builder()
            .id(userId)
            .email("test@example.com")
            .fullName("Hong Gildong")
            .timeZone("Asia/Seoul")
            .build();
    List<DiaryReminder> reminders =
        List.of(
            DiaryReminder.builder().user(user).reminderTime(LocalTime.of(9, 0)).enabled(false).build(),
            DiaryReminder.builder().user(user).reminderTime(LocalTime.of(21, 0)).enabled(true).build());
    UserProfileResponse response =
        new UserProfileResponse(
            userId,
            null,
            "unknown",
            null,
            List.of(),
            List.of(),
            Map.of(),
            "Asia/Seoul",
            List.of(
                new UserProfileResponse.DiaryReminder("09:00", false),
                new UserProfileResponse.DiaryReminder("21:00", true)),
            3);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(userId)).willReturn(reminders);
    given(userConverter.toUserProfileResponse(user, reminders)).willReturn(response);

    UserProfileResponse result = userService.getUserProfile(userId);

    assertThat(result.getTimeZone()).isEqualTo("Asia/Seoul");
    assertThat(result.getDiaryReminderMaxCount()).isEqualTo(3);
    assertThat(result.getDiaryReminders())
        .extracting(UserProfileResponse.DiaryReminder::time)
        .containsExactly("09:00", "21:00");
    verify(diaryReminderRepository).findByUserIdOrderByReminderTimeAsc(userId);
    verify(userConverter).toUserProfileResponse(user, reminders);
  }
}
