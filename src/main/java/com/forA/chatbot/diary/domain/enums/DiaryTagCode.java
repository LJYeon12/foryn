package com.forA.chatbot.diary.domain.enums;

import static com.forA.chatbot.diary.domain.enums.DiaryTagType.ADHD_SPECIAL;
import static com.forA.chatbot.diary.domain.enums.DiaryTagType.LIFE;
import static com.forA.chatbot.diary.domain.enums.DiaryTagType.NEGATIVE;
import static com.forA.chatbot.diary.domain.enums.DiaryTagType.NEUTRAL;
import static com.forA.chatbot.diary.domain.enums.DiaryTagType.POSITIVE;
import static com.forA.chatbot.diary.domain.enums.DiaryTagType.RELATIONSHIP;
import static com.forA.chatbot.diary.domain.enums.DiaryTagType.WORK_STUDY;

public enum DiaryTagCode {
  ASSIGNMENT(WORK_STUDY, "과제"),
  WORK(WORK_STUDY, "업무"),
  EXAM(WORK_STUDY, "시험"),
  DEADLINE(WORK_STUDY, "마감"),
  PRESENTATION(WORK_STUDY, "발표"),
  CONCENTRATION_PROBLEM(WORK_STUDY, "집중 문제"),
  TASK_PROCRASTINATION(WORK_STUDY, "미룸"),
  FAMILY(RELATIONSHIP, "가족"),
  FRIEND(RELATIONSHIP, "친구"),
  PARTNER(RELATIONSHIP, "연인"),
  CONFLICT(RELATIONSHIP, "갈등"),
  CONTACT_PROBLEM(RELATIONSHIP, "연락 문제"),
  REJECTION(RELATIONSHIP, "거절 당함"),
  SLEEP_DEPRIVATION(LIFE, "수면 부족"),
  FATIGUE(LIFE, "피곤함"),
  MEAL(LIFE, "식사"),
  EXERCISE(LIFE, "운동"),
  MEDICATION(LIFE, "약 복용"),
  PERIOD(LIFE, "생리"),
  CAFFEINE(LIFE, "카페인"),
  SNS(LIFE, "SNS"),
  GAME(LIFE, "게임"),
  OUTING(LIFE, "외출"),
  STAYING_HOME(LIFE, "집에만 있음"),
  ALONE(LIFE, "혼자 있음"),
  COMFORTABLE(POSITIVE, "편안함"),
  STABILITY(POSITIVE, "안정감"),
  PROUD(POSITIVE, "뿌듯함"),
  ACHIEVEMENT(POSITIVE, "성취감"),
  HAPPINESS(POSITIVE, "행복함"),
  EXPECTATION(POSITIVE, "기대됨"),
  ENJOYMENT(POSITIVE, "즐거움"),
  CONFIDENCE(POSITIVE, "자신감"),
  GRATITUDE(POSITIVE, "감사함"),
  EXCITEMENT(POSITIVE, "설렘"),
  INDIFFERENCE(NEUTRAL, "무덤덤함"),
  BLANKNESS(NEUTRAL, "멍함"),
  NORMAL(NEUTRAL, "평범함"),
  EXHAUSTION(NEUTRAL, "지침"),
  MANY_THOUGHTS(NEUTRAL, "생각 많음"),
  SENSITIVE(NEUTRAL, "예민함"),
  ANXIETY(NEGATIVE, "불안"),
  DEPRESSION(NEGATIVE, "우울함"),
  LONELINESS(NEGATIVE, "외로움"),
  EMPTINESS(NEGATIVE, "공허함"),
  IRRITATION(NEGATIVE, "짜증"),
  ANGER(NEGATIVE, "화남"),
  GUILT(NEGATIVE, "죄책감"),
  BURDEN(NEGATIVE, "부담감"),
  LETHARGY(NEGATIVE, "무기력"),
  FRUSTRATION(NEGATIVE, "답답함"),
  NERVOUSNESS(NEGATIVE, "초조함"),
  STRESS(NEGATIVE, "스트레스"),
  SHAME(NEGATIVE, "수치심"),
  DISCOURAGEMENT(NEGATIVE, "좌절감"),
  CONFUSION(NEGATIVE, "혼란스러움"),
  OVERLOAD(ADHD_SPECIAL, "과부하 느낌"),
  FOCUS_PROBLEM(ADHD_SPECIAL, "집중 안 됨"),
  DISTRACTION(ADHD_SPECIAL, "산만함"),
  PRESSURE(ADHD_SPECIAL, "압박감"),
  PROCRASTINATION(ADHD_SPECIAL, "미루고 있음"),
  SELF_BLAME(ADHD_SPECIAL, "자책함"),
  EMOTIONAL_OUTBURST(ADHD_SPECIAL, "감정폭발"),
  REJECTION_SENSITIVITY(ADHD_SPECIAL, "거절 당한 느낌");

  private final DiaryTagType tagType;
  private final String label;

  DiaryTagCode(DiaryTagType tagType, String label) {
    this.tagType = tagType;
    this.label = label;
  }

  public DiaryTagType getTagType() {
    return tagType;
  }

  public String getLabel() {
    return label;
  }
}
