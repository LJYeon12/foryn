package com.forA.chatbot.user.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.dto.UserProfileResponse;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserConverterTest {

  private final UserConverter userConverter = new UserConverter();

  @Test
  @DisplayName("toUserProfileResponse maps timezone and sorted diary reminders")
  void toUserProfileResponse_mapsDiaryReminders() {
    User user =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .fullName("Hong Gildong")
            .gender(Gender.UNKNOWN)
            .timeZone("Asia/Seoul")
            .build();
    List<DiaryReminder> reminders =
        List.of(
            DiaryReminder.builder().user(user).reminderTime(LocalTime.of(21, 0)).enabled(true).build(),
            DiaryReminder.builder().user(user).reminderTime(LocalTime.of(9, 0)).enabled(false).build());

    UserProfileResponse response = userConverter.toUserProfileResponse(user, reminders);

    assertThat(response.getTimeZone()).isEqualTo("Asia/Seoul");
    assertThat(response.getDiaryReminderMaxCount()).isEqualTo(3);
    assertThat(response.getDiaryReminders())
        .extracting(UserProfileResponse.DiaryReminder::time)
        .containsExactly("09:00", "21:00");
    assertThat(response.getDiaryReminders())
        .extracting(UserProfileResponse.DiaryReminder::enabled)
        .containsExactly(false, true);
  }

  @Test
  @DisplayName("toUserProfileResponse requires explicit diary reminders")
  void toUserProfileResponse_requiresDiaryReminders() {
    boolean hasUserOnlyOverload =
        Arrays.stream(UserConverter.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("toUserProfileResponse"))
            .map(Method::getParameterTypes)
            .anyMatch(parameterTypes ->
                parameterTypes.length == 1 && parameterTypes[0].equals(User.class));

    assertThat(hasUserOnlyOverload).isFalse();
  }
}
