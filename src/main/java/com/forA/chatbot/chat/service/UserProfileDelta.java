package com.forA.chatbot.chat.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 메모리 내 사본만 사용한다. 이 값을 로그나 작업 일지에 저장하지 않는다. */
public record UserProfileDelta(Profile before, Profile after) {
    public record Profile(Gender gender, Integer birthYear, Set<JobType> jobs,
            Set<DisorderType> disorders, Set<SymptomType> symptoms) {
        static Profile from(User user) {
            return new Profile(user.getGender(), user.getBirthYear(), copy(user.getJobs()),
                copy(user.getDisorders()), copy(user.getSymptoms()));
        }
    }

    public static User workingCopy(User user) {
        return User.builder().id(user.getId()).nickname(user.getNickname()).firstName(user.getFirstName())
            .lastName(user.getLastName()).fullName(user.getFullName()).gender(user.getGender())
            .birthYear(user.getBirthYear()).jobs(copy(user.getJobs())).disorders(copy(user.getDisorders()))
            .symptoms(copy(user.getSymptoms())).timeZone(user.getTimeZone()).build();
    }

    public static UserProfileDelta between(User before, User after) {
        return new UserProfileDelta(Profile.from(before), Profile.from(after));
    }

    public boolean changed() { return !before.equals(after); }

    public void applyTo(User latest) {
        Profile current = Profile.from(latest);
        if (!Objects.equals(before.gender(), after.gender())) {
            requireUnchanged(before.gender(), current.gender());
            latest.updateGender(after.gender());
        }
        if (!Objects.equals(before.birthYear(), after.birthYear())) {
            requireUnchanged(before.birthYear(), current.birthYear());
            latest.updateBirthYear(after.birthYear());
        }
        if (!before.jobs().equals(after.jobs())) {
            requireUnchanged(before.jobs(), current.jobs());
            latest.updateJobs(new HashSet<>(after.jobs()));
        }
        if (!before.disorders().equals(after.disorders())) {
            requireUnchanged(before.disorders(), current.disorders());
            latest.updateDisorders(new HashSet<>(after.disorders()));
        }
        if (!before.symptoms().equals(after.symptoms())) {
            requireUnchanged(before.symptoms(), current.symptoms());
            latest.updateSymptoms(new HashSet<>(after.symptoms()));
        }
    }

    private static void requireUnchanged(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new ChatHandler(ErrorStatus.CHAT_STATE_CONFLICT);
    }

    private static <T> Set<T> copy(Set<T> values) { return values == null ? Set.of() : Set.copyOf(values); }
}
