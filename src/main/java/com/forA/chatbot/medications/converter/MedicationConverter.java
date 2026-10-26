package com.forA.chatbot.medications.converter;

import com.forA.chatbot.enums.DayOfWeek;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationItem;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.dto.MedicationLogDto;
import com.forA.chatbot.medications.dto.MedicationLogResponseDto;
import com.forA.chatbot.medications.dto.MedicationRequestDto;
import com.forA.chatbot.medications.dto.MedicationResponseDto;
import com.forA.chatbot.medications.dto.TodayMedicationResponseDto;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MedicationConverter {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  public MedicationBundle toMedicationBundle(
      User user, MedicationRequestDto requestDto, LocalTime scheduledTimeUtc) {
    return MedicationBundle.builder()
        .user(user)
        .bundleName(requestDto.getName())
        .dayOfWeek(toDayOfWeekColumn(requestDto.getTakeDays()))
        .scheduledTime(scheduledTimeUtc)
        .build();
  }

  public List<MedicationItem> toMedicationItems(MedicationBundle bundle, List<String> typeTags) {
    return typeTags.stream()
        .map(
            typeTag ->
                MedicationItem.builder()
                    .medicationBundle(bundle)
                    .medicationName(typeTag)
                    .build())
        .collect(Collectors.toList());
  }

  public String toDayOfWeekColumn(List<String> takeDays) {
    return takeDays.stream()
        .map(day -> DayOfWeek.from(day).name())
        .collect(Collectors.joining(","));
  }

  public MedicationResponseDto toMedicationResponse(
      MedicationBundle bundle, List<MedicationItem> items) {
    return MedicationResponseDto.builder()
        .id(bundle.getId())
        .name(bundle.getBundleName())
        .typeTags(items.stream().map(MedicationItem::getMedicationName).collect(Collectors.toList()))
        .takeDays(splitDayOfWeek(bundle.getDayOfWeek()))
        .takeTime(bundle.getScheduledTime().format(TIME_FORMATTER))
        .createdAt(bundle.getCreatedAt())
        .build();
  }

  public MedicationLogResponseDto toMedicationLogResponse(MedicationLog entity) {
    MedicationLogDto medicationLog =
        MedicationLogDto.builder()
            .medicationId(entity.getMedicationBundle().getId())
            .date(entity.getDate().toString())
            .status(Boolean.TRUE.equals(entity.getIsTaken()) ? "TAKEN" : "SKIPPED")
            .conditionLevel(entity.getMedCondition())
            .build();

    return MedicationLogResponseDto.builder()
        .historyId(entity.getId())
        .message("Medication history successfully recorded.")
        .medicationLog(medicationLog)
        .build();
  }

  public TodayMedicationResponseDto toTodayMedicationResponse(
      MedicationBundle bundle,
      LocalDate date,
      List<MedicationItem> items,
      MedicationLog historyLog,
      LocalTime localTakeTime) {
    return TodayMedicationResponseDto.builder()
        .medicationId(bundle.getId())
        .name(bundle.getBundleName())
        .date(date)
        .takeTime(localTakeTime)
        .typeTags(items.stream().map(MedicationItem::getMedicationName).collect(Collectors.toList()))
        .takeDays(splitDayOfWeek(bundle.getDayOfWeek()))
        .todayHistory(toTodayHistory(historyLog))
        .build();
  }

  public TodayMedicationResponseDto.TodayHistory toTodayHistory(MedicationLog historyLog) {
    if (historyLog == null) {
      return TodayMedicationResponseDto.TodayHistory.builder()
          .status("PENDING")
          .conditionLevel(null)
          .build();
    }

    return TodayMedicationResponseDto.TodayHistory.builder()
        .status(Boolean.TRUE.equals(historyLog.getIsTaken()) ? "TAKEN" : "SKIPPED")
        .conditionLevel(historyLog.getMedCondition())
        .build();
  }

  private List<String> splitDayOfWeek(String dayOfWeekColumn) {
    return Arrays.asList(dayOfWeekColumn.split(","));
  }
}
