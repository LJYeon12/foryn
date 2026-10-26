package com.forA.chatbot.diary.service;

import java.time.LocalDate;
import java.util.List;

public record RecentDiaryTagSummary(
    boolean hasRecentDiaryData,
    List<TagFrequency> tagFrequencies,
    List<DailyTagGroup> dailyTagGroups
) {

  public static RecentDiaryTagSummary empty() {
    return new RecentDiaryTagSummary(false, List.of(), List.of());
  }

  public boolean hasTags() {
    return tagFrequencies != null && !tagFrequencies.isEmpty();
  }

  public record TagFrequency(
      String tagType,
      String tagCode,
      String label,
      long count
  ) {
  }

  public record DailyTagGroup(
      LocalDate date,
      List<String> labels
  ) {
  }
}
