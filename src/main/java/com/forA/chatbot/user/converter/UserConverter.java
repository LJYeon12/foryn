package com.forA.chatbot.user.converter;

import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import com.forA.chatbot.user.dto.UserProfileResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserConverter {

  private static final int DIARY_REMINDER_MAX_COUNT = 3;

  public UserProfileResponse toUserProfileResponse(User user, List<DiaryReminder> diaryReminders) {
    String gender = user.getGender() != null ? user.getGender().name().toLowerCase() : null;
    List<String> job = user.getJobs().stream().map(JobType::getName).collect(Collectors.toList());
    List<String> mentalConditions =
        user.getDisorders().stream().map(DisorderType::getName).collect(Collectors.toList());
    Map<String, List<String>> symptoms =
        user.getSymptoms().stream()
            .collect(
                Collectors.groupingBy(
                    symptom -> symptom.getDisorderType().getName(),
                    Collectors.mapping(SymptomType::getDescription, Collectors.toList())));
    List<UserProfileResponse.DiaryReminder> reminderItems =
        diaryReminders.stream()
            // user/me 응답은 문서 정책대로 알림 시간을 오름차순으로 고정한다.
            .sorted(Comparator.comparing(DiaryReminder::getReminderTime))
            .map(reminder -> new UserProfileResponse.DiaryReminder(
                reminder.getReminderTime().toString(),
                reminder.isEnabled()))
            .toList();
    return new UserProfileResponse(user.getId(), user.getNickname(), gender, user.getBirthYear(),
        job, mentalConditions, symptoms, user.getTimeZone(), reminderItems, DIARY_REMINDER_MAX_COUNT);
  }
}
