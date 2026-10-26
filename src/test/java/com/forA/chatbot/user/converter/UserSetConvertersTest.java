package com.forA.chatbot.user.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSetConvertersTest {

  private final JobSetConverter jobSetConverter = new JobSetConverter();
  private final DisorderSetConverter disorderSetConverter = new DisorderSetConverter();
  private final SymptomSetConverter symptomSetConverter = new SymptomSetConverter();

  @Test
  @DisplayName("job converter serializes sorted job ids to json")
  void jobSetConverter_convertToDatabaseColumn() {
    String json =
        jobSetConverter.convertToDatabaseColumn(
            Set.of(JobType.FREELANCER, JobType.OFFICE_WORKER));

    assertThat(json).isEqualTo("[3,5]");
  }

  @Test
  @DisplayName("job converter returns empty set for invalid json")
  void jobSetConverter_invalidJson() {
    assertThat(jobSetConverter.convertToEntityAttribute("invalid-json")).isEmpty();
  }

  @Test
  @DisplayName("disorder converter restores disorders from json ids")
  void disorderSetConverter_convertToEntityAttribute() {
    Set<DisorderType> disorders = disorderSetConverter.convertToEntityAttribute("[1,3]");

    assertThat(disorders)
        .containsExactlyInAnyOrder(DisorderType.DEPRESSION, DisorderType.ANXIETY_DISORDER);
  }

  @Test
  @DisplayName("disorder converter writes empty array for null input")
  void disorderSetConverter_nullInput() {
    assertThat(disorderSetConverter.convertToDatabaseColumn(null)).isEqualTo("[]");
  }

  @Test
  @DisplayName("symptom converter restores symptoms from json ids")
  void symptomSetConverter_convertToEntityAttribute() {
    Set<SymptomType> symptoms = symptomSetConverter.convertToEntityAttribute("[1,13]");

    assertThat(symptoms)
        .containsExactlyInAnyOrder(
            SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES,
            SymptomType.CONSTANT_FUTURE_WORRIES);
  }

  @Test
  @DisplayName("symptom converter returns empty set for empty json array")
  void symptomSetConverter_emptyArray() {
    assertThat(symptomSetConverter.convertToEntityAttribute("[]")).isEmpty();
  }
}
