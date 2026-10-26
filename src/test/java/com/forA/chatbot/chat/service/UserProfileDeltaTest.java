package com.forA.chatbot.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.JobType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserProfileDeltaTest {
    @Test void preparationDoesNotMutateManagedUserAndOnlyChangedFieldsAreApplied() {
        User before = User.builder().id(1L).nickname("before").build();
        User working = UserProfileDelta.workingCopy(before);
        working.updateGender(Gender.MALE);
        assertThat(before.getGender()).isEqualTo(Gender.UNKNOWN);
        User latest = UserProfileDelta.workingCopy(before);
        latest.updateNickname("changed-by-another-request");
        latest.updateBirthYear(1995);
        UserProfileDelta.between(before, working).applyTo(latest);
        assertThat(latest.getGender()).isEqualTo(Gender.MALE);
        assertThat(latest.getNickname()).isEqualTo("changed-by-another-request");
        assertThat(latest.getBirthYear()).isEqualTo(1995);
    }

    @Test void staleTargetProfileCannotOverwriteConcurrentChange() {
        User before = User.builder().id(1L).build();
        User working = UserProfileDelta.workingCopy(before);
        working.updateJobs(Set.of(JobType.values()[0]));
        User latest = UserProfileDelta.workingCopy(before);
        latest.updateJobs(Set.of(JobType.values()[1]));
        assertThatThrownBy(() -> UserProfileDelta.between(before, working).applyTo(latest)).isInstanceOf(ChatHandler.class);
        assertThat(latest.getJobs()).containsExactly(JobType.values()[1]);
    }
}
