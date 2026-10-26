package com.forA.chatbot.user.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserProfileRequestConverterTest {

  private final UserProfileRequestConverter converter = new UserProfileRequestConverter();

  @Test
  @DisplayName("convertJobs returns empty set for null input")
  void convertJobs_nullInput() {
    assertThat(converter.convertJobs(null)).isEmpty();
  }

  @Test
  @DisplayName("convertJobs converts job names to enum set")
  void convertJobs_success() {
    Set<JobType> jobs =
        converter.convertJobs(List.of(JobType.OFFICE_WORKER.getName(), JobType.FREELANCER.getName()));

    assertThat(jobs).containsExactlyInAnyOrder(JobType.OFFICE_WORKER, JobType.FREELANCER);
  }

  @Test
  @DisplayName("convertDisorders throws for unknown disorder name")
  void convertDisorders_invalidName() {
    assertThatThrownBy(() -> converter.convertDisorders(List.of("unknown-disorder")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown-disorder");
  }

  @Test
  @DisplayName("convertSymptoms converts symptoms within matching disorder only")
  void convertSymptoms_success() {
    Map<String, List<String>> symptomsMap =
        Map.of(
            DisorderType.DEPRESSION.getName(),
            List.of(
                SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription(),
                SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription()),
            DisorderType.ANXIETY_DISORDER.getName(),
            List.of(SymptomType.CONSTANT_FUTURE_WORRIES.getDescription()));

    Set<SymptomType> symptoms = converter.convertSymptoms(symptomsMap);

    assertThat(symptoms)
        .containsExactlyInAnyOrder(
            SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES,
            SymptomType.LETHARGY_AND_UNWILLINGNESS,
            SymptomType.CONSTANT_FUTURE_WORRIES);
  }

  @Test
  @DisplayName("convertSymptoms accepts exactly 3 symptoms for one disorder")
  void convertSymptoms_threeSymptomsPerDisorder_success() {
    Map<String, List<String>> symptomsMap =
        Map.of(
            DisorderType.DEPRESSION.getName(),
            List.of(
                SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription(),
                SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription(),
                SymptomType.FEELING_WORTHLESS.getDescription()));

    assertThat(converter.convertSymptoms(symptomsMap))
        .containsExactlyInAnyOrder(
            SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES,
            SymptomType.LETHARGY_AND_UNWILLINGNESS,
            SymptomType.FEELING_WORTHLESS);
  }

  @Test
  @DisplayName("convertSymptoms accepts 3 symptoms per disorder across 2 disorders")
  void convertSymptoms_threeSymptomsPerDisorderAcrossTwoDisorders_success() {
    Map<String, List<String>> symptomsMap =
        Map.of(
            DisorderType.DEPRESSION.getName(),
            List.of(
                SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription(),
                SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription(),
                SymptomType.FEELING_WORTHLESS.getDescription()),
            DisorderType.ANXIETY_DISORDER.getName(),
            List.of(
                SymptomType.CONSTANT_FUTURE_WORRIES.getDescription(),
                SymptomType.FEAR_OF_MAKING_MISTAKES.getDescription(),
                SymptomType.CHEST_TIGHTNESS_AND_BREATHLESSNESS.getDescription()));

    assertThat(converter.convertSymptoms(symptomsMap)).hasSize(6);
  }

  @Test
  @DisplayName("convertSymptoms throws when a disorder has more than 3 symptoms")
  void convertSymptoms_limitExceeded_throwsUserHandler() {
    Map<String, List<String>> symptomsMap =
        Map.of(
            DisorderType.DEPRESSION.getName(),
            List.of(
                SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES.getDescription(),
                SymptomType.LETHARGY_AND_UNWILLINGNESS.getDescription(),
                SymptomType.FEELING_WORTHLESS.getDescription(),
                SymptomType.DIFFICULTY_CONCENTRATING.getDescription()));

    assertThatThrownBy(() -> converter.convertSymptoms(symptomsMap))
        .isInstanceOf(UserHandler.class)
        .hasFieldOrPropertyWithValue("code", ErrorStatus.USER_SYMPTOM_DISORDER_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("convertSymptoms throws when symptom does not belong to disorder")
  void convertSymptoms_invalidSymptomForDisorder() {
    Map<String, List<String>> symptomsMap =
        Map.of(
            DisorderType.DEPRESSION.getName(),
            List.of(SymptomType.CONSTANT_FUTURE_WORRIES.getDescription()));

    assertThatThrownBy(() -> converter.convertSymptoms(symptomsMap))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(DisorderType.DEPRESSION.getName());
  }
}
