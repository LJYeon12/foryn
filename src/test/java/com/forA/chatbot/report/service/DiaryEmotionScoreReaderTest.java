package com.forA.chatbot.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.forA.chatbot.diary.domain.Diary;
import com.forA.chatbot.diary.domain.enums.DiaryStatus;
import com.forA.chatbot.diary.repository.DiaryRepository;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiaryEmotionScoreReaderTest {

  @Mock
  private DiaryRepository diaryRepository;

  @Test
  @DisplayName("findWeightedEmotionScoresByDate returns completed diary scores twice")
  void findWeightedEmotionScoresByDate_returnsDiaryScoresTwice() {
    Long userId = 1L;
    LocalDate targetDate = LocalDate.of(2026, 6, 25);
    LocalDate startDate = LocalDate.of(2026, 6, 1);
    LocalDate endDate = LocalDate.of(2026, 6, 30);
    Diary diary = Diary.builder()
        .user(User.builder().id(userId).email("user@example.com").fullName("Test User").build())
        .diaryDate(targetDate)
        .status(DiaryStatus.COMPLETED)
        .momentAnswer("순간")
        .thoughtAnswer("생각")
        .emotionAnswer("감정")
        .forynComment("코멘트")
        .emotionScore(2)
        .build();

    given(diaryRepository.findCompletedEmotionScoresByUserIdAndDiaryDateBetween(
            userId, startDate, endDate))
        .willReturn(List.of(diary));

    DiaryEmotionScoreReader reader = new DiaryEmotionScoreReader(diaryRepository);

    Map<LocalDate, List<Integer>> result =
        reader.findWeightedEmotionScoresByDate(userId, startDate, endDate);

    assertThat(result).containsEntry(targetDate, List.of(2, 2));
  }
}
