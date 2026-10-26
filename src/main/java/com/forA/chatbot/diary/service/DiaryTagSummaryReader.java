package com.forA.chatbot.diary.service;

import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.domain.enums.DiaryTagCode;
import com.forA.chatbot.diary.repository.DiaryRepository;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryTagSummaryReader {

  private final DiaryRepository diaryRepository;

  public RecentDiaryTagSummary readRecentTagSummary(
      User user, LocalDate startDate, LocalDate endDate) {
    List<Diary> diaries =
        diaryRepository.findCompletedByUserIdAndDiaryDateBetweenOrderByDiaryDateAscCompletedAtAsc(
            user.getId(), startDate, endDate);
    if (diaries.isEmpty()) {
      return RecentDiaryTagSummary.empty();
    }

    Map<DiaryTagCode, Long> countsByTag = new LinkedHashMap<>();
    Map<LocalDate, List<String>> labelsByDate = new LinkedHashMap<>();
    for (Diary diary : diaries) {
      List<String> dailyLabels = labelsByDate.computeIfAbsent(diary.getDiaryDate(), ignored -> new ArrayList<>());
      for (DiaryTagCode tagCode : diary.getTagCodes()) {
        countsByTag.merge(tagCode, 1L, Long::sum);
        dailyLabels.add(tagCode.getLabel());
      }
    }

    List<RecentDiaryTagSummary.TagFrequency> tagFrequencies =
        countsByTag.entrySet().stream()
            .sorted(Map.Entry.<DiaryTagCode, Long>comparingByValue(Comparator.reverseOrder()))
            .map(entry -> new RecentDiaryTagSummary.TagFrequency(
                entry.getKey().getTagType().name(),
                entry.getKey().name(),
                entry.getKey().getLabel(),
                entry.getValue()))
            .toList();
    List<RecentDiaryTagSummary.DailyTagGroup> dailyTagGroups =
        labelsByDate.entrySet().stream()
            .map(entry -> new RecentDiaryTagSummary.DailyTagGroup(entry.getKey(), entry.getValue()))
            .toList();

    return new RecentDiaryTagSummary(true, tagFrequencies, dailyTagGroups);
  }
}
