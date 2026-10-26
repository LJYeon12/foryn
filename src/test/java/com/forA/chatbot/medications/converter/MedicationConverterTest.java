package com.forA.chatbot.medications.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forA.chatbot.apiPayload.exception.handler.MedicationHandler;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationItem;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.dto.MedicationRequestDto;
import com.forA.chatbot.medications.dto.MedicationResponseDto;
import com.forA.chatbot.medications.dto.TodayMedicationResponseDto;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MedicationConverterTest {

  private final MedicationConverter medicationConverter = new MedicationConverter();

  @Test
  @DisplayName("toDayOfWeekColumn normalizes request values to uppercase enum names")
  void toDayOfWeekColumn_normalizesRequestValues() {
    String dayOfWeekColumn = medicationConverter.toDayOfWeekColumn(List.of("monday", "Wednesday"));

    assertThat(dayOfWeekColumn).isEqualTo("MONDAY,WEDNESDAY");
  }

  @Test
  @DisplayName("toDayOfWeekColumn throws medication handler for invalid day")
  void toDayOfWeekColumn_invalidDay() {
    assertThatThrownBy(() -> medicationConverter.toDayOfWeekColumn(List.of("holiday")))
        .isInstanceOf(MedicationHandler.class);
  }

  @Test
  @DisplayName("toMedicationBundle converts request and UTC time to bundle entity")
  void toMedicationBundle_createsBundleEntity() {
    User user = User.builder().id(1L).email("user@example.com").build();
    MedicationRequestDto requestDto =
        MedicationRequestDto.builder()
            .name("vitamin")
            .typeTags(List.of("SUPPLEMENT"))
            .takeDays(List.of("monday", "friday"))
            .takeTime("09:00")
            .build();

    MedicationBundle bundle =
        medicationConverter.toMedicationBundle(user, requestDto, LocalTime.of(0, 0));

    assertThat(bundle.getUser()).isEqualTo(user);
    assertThat(bundle.getBundleName()).isEqualTo("vitamin");
    assertThat(bundle.getDayOfWeek()).isEqualTo("MONDAY,FRIDAY");
    assertThat(bundle.getScheduledTime()).isEqualTo(LocalTime.MIDNIGHT);
  }

  @Test
  @DisplayName("toMedicationResponse maps bundle and items to response DTO")
  void toMedicationResponse_mapsEntityToResponse() {
    MedicationBundle bundle = medicationBundle("vitamin", "MONDAY,WEDNESDAY", LocalTime.of(9, 0));
    ReflectionTestUtils.setField(bundle, "id", 10L);
    ReflectionTestUtils.setField(bundle, "createdAt", LocalDateTime.of(2026, 3, 17, 9, 30));

    List<MedicationItem> items =
        List.of(
            medicationItem(bundle, "SUPPLEMENT"),
            medicationItem(bundle, "CAPSULE"));

    MedicationResponseDto response = medicationConverter.toMedicationResponse(bundle, items);

    assertThat(response.getId()).isEqualTo(10L);
    assertThat(response.getName()).isEqualTo("vitamin");
    assertThat(response.getTypeTags()).containsExactly("SUPPLEMENT", "CAPSULE");
    assertThat(response.getTakeDays()).containsExactly("MONDAY", "WEDNESDAY");
    assertThat(response.getTakeTime()).isEqualTo("09:00");
    assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 17, 9, 30));
  }

  @Test
  @DisplayName("toMedicationLogResponse maps medication log to response DTO")
  void toMedicationLogResponse_mapsLogToResponse() {
    MedicationBundle bundle = medicationBundle("vitamin", "MONDAY", LocalTime.of(9, 0));
    ReflectionTestUtils.setField(bundle, "id", 7L);

    MedicationLog log =
        MedicationLog.builder()
            .medicationBundle(bundle)
            .date(LocalDate.of(2026, 3, 17))
            .isTaken(true)
            .medCondition(2)
            .build();
    ReflectionTestUtils.setField(log, "id", 100L);

    var response = medicationConverter.toMedicationLogResponse(log);

    assertThat(response.getHistoryId()).isEqualTo(100L);
    assertThat(response.getMessage()).isEqualTo("Medication history successfully recorded.");
    assertThat(response.getMedicationLog().getMedicationId()).isEqualTo(7L);
    assertThat(response.getMedicationLog().getDate()).isEqualTo("2026-03-17");
    assertThat(response.getMedicationLog().getStatus()).isEqualTo("TAKEN");
    assertThat(response.getMedicationLog().getConditionLevel()).isEqualTo(2);
  }

  @Test
  @DisplayName("toTodayMedicationResponse returns pending history when no log exists")
  void toTodayMedicationResponse_withoutLog() {
    MedicationBundle bundle = medicationBundle("omega3", "TUESDAY,THURSDAY", LocalTime.of(0, 0));
    ReflectionTestUtils.setField(bundle, "id", 20L);

    TodayMedicationResponseDto response =
        medicationConverter.toTodayMedicationResponse(
            bundle,
            LocalDate.of(2026, 3, 17),
            List.of(medicationItem(bundle, "SOFTGEL")),
            null,
            LocalTime.of(9, 0));

    assertThat(response.getMedicationId()).isEqualTo(20L);
    assertThat(response.getTakeDays()).containsExactly("TUESDAY", "THURSDAY");
    assertThat(response.getTodayHistory().getStatus()).isEqualTo("PENDING");
    assertThat(response.getTodayHistory().getConditionLevel()).isNull();
  }

  private MedicationBundle medicationBundle(
      String name, String dayOfWeek, LocalTime scheduledTime) {
    return MedicationBundle.builder()
        .user(User.builder().id(1L).email("user@example.com").build())
        .bundleName(name)
        .dayOfWeek(dayOfWeek)
        .scheduledTime(scheduledTime)
        .build();
  }

  private MedicationItem medicationItem(MedicationBundle bundle, String typeTag) {
    return MedicationItem.builder()
        .medicationBundle(bundle)
        .medicationName(typeTag)
        .build();
  }
}
