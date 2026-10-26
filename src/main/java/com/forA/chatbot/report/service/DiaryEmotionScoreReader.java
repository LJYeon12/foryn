package com.forA.chatbot.report.service;

import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.repository.DiaryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryEmotionScoreReader {

  private final DiaryRepository diaryRepository;

  public Map<LocalDate, List<Integer>> findWeightedEmotionScoresByDate(
      Long userId, LocalDate startDate, LocalDate endDate) {
    List<Diary> diaries =
        diaryRepository.findCompletedEmotionScoresByUserIdAndDiaryDateBetween(
            userId, startDate, endDate);
    Map<LocalDate, List<Integer>> scoresByDate = new HashMap<>();

    for (Diary diary : diaries) {
      Integer score = diary.getEmotionScore();
      if (score == null) {
        continue;
      }
      List<Integer> dailyScores =
          scoresByDate.computeIfAbsent(diary.getDiaryDate(), ignored -> new ArrayList<>());
      // 한줄일기는 기존 채팅/복약 감정보다 강한 신호이므로 같은 점수를 2회 넣어 2배 가중치를 준다.
      dailyScores.add(score);
      dailyScores.add(score);
    }

    return scoresByDate;
  }
}
