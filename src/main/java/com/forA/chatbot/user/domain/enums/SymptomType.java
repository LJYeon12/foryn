package com.forA.chatbot.user.domain.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SymptomType {
  // 우울증 증상 (1-6)
  NO_INTEREST_IN_ENJOYABLE_ACTIVITIES(1, "예전엔 좋아하던 일도, 이제는 아무런 재미가 없어요", DisorderType.DEPRESSION),
  LETHARGY_AND_UNWILLINGNESS(2, "아무것도 하기 싫고 온종일 무기력해요", DisorderType.DEPRESSION),
  FEELING_WORTHLESS(3, "내가 쓸모없는 사람처럼 느껴져요", DisorderType.DEPRESSION),
  FEELING_BURDENSOME_TO_OTHERS(4, "자꾸만 주변 사람들에게 피해만 주는 것 같아요", DisorderType.DEPRESSION),
  TIRED_ALL_DAY_DESPITE_SLEEP(5, "아무리 자도 개운하지 않고 하루 종일 피곤해요", DisorderType.DEPRESSION),
  DIFFICULTY_CONCENTRATING(6, "생각이 정리되지 않고 집중이 너무 힘들어요", DisorderType.DEPRESSION),

  // 조울증 증상 (7-12)
  EXTREME_MOOD_SWINGS(7, "기분이 너무 들떴다가 갑자기 가라앉을 때가 있어요", DisorderType.BIPOLAR_DISORDER),
  EXCESSIVE_ENERGY_WITHOUT_SLEEP(
      8, "잠을 거의 안 자도 피곤하지 않고 지나치게 에너지가 넘쳐요", DisorderType.BIPOLAR_DISORDER),
  RACING_THOUGHTS_AND_TALKATIVENESS(9, "생각이 너무 빨리 돌아가고 말이 많아져요", DisorderType.BIPOLAR_DISORDER),
  EXCESSIVE_CONFIDENCE(10, "평소보다 자신감이 과하게 올라갈 때가 있어요", DisorderType.BIPOLAR_DISORDER),
  TOO_MANY_PLANS_AND_ACTIVITIES(11, "계획을 너무 많이 세우고 활동을 지나치게 벌여요", DisorderType.BIPOLAR_DISORDER),
  IMPULSIVE_SPENDING_AND_RECKLESS_BEHAVIOR(
      12, "충동적으로 지출하거나, 무모한 행동을 하고 후회해요", DisorderType.BIPOLAR_DISORDER),

  // 불안장애 증상 (13-18)
  CONSTANT_FUTURE_WORRIES(13, "늘 미래에 대한 걱정이 머릿속을 떠나지 않아요", DisorderType.ANXIETY_DISORDER),
  FEAR_OF_MAKING_MISTAKES(14, "실수에 대한 두려움이 커서 시작을 못 해요", DisorderType.ANXIETY_DISORDER),
  ANXIETY_AFFECTING_CONCENTRATION_AND_TREMBLING(
      15, "불안해서 집중이 안 되고 자꾸 손이 떨려요", DisorderType.ANXIETY_DISORDER),
  EXCESSIVE_CONCERN_ABOUT_OTHERS_OPINIONS(
      16, "다른 사람이 나를 어떻게 볼지 너무 신경 쓰여요", DisorderType.ANXIETY_DISORDER),
  DIFFICULTY_FALLING_ASLEEP_DUE_TO_ANXIETY(17, "불안해서 잠드는 데 한참 걸려요", DisorderType.ANXIETY_DISORDER),
  CHEST_TIGHTNESS_AND_BREATHLESSNESS(18, "가슴이 답답하고 숨이 막히는 기분이 들어요", DisorderType.ANXIETY_DISORDER),

  // 공황장애 증상 (19-24)
  SUDDEN_RAPID_HEARTBEAT_AND_SHORTNESS_OF_BREATH(
      19, "갑자기 심장이 빨리 뛰고 숨이 가빠질 때가 있어요", DisorderType.PANIC_DISORDER),
  TREMBLING_COLD_SWEATS_AND_DIZZINESS(
      20, "몸이 떨리고 식은땀이 나거나, 어지러워 쓰러질 것 같아요", DisorderType.PANIC_DISORDER),
  FEELING_LIKE_DYING_IMMEDIATELY(21, "지금 당장 죽을 것 같다는 생각이 들 때가 있어요", DisorderType.PANIC_DISORDER),
  FEAR_OF_GOING_OUT_DUE_TO_PANIC(22, "또 공황이 올까 봐 외출이나 사람 만나는 게 두려워요", DisorderType.PANIC_DISORDER),
  FEELING_SERIOUSLY_ILL(23, "몸에 심각한 이상이 생긴 것처럼 자주 느껴져요", DisorderType.PANIC_DISORDER),
  FEAR_OF_ENCLOSED_SPACES(24, "엘리베이터나 지하철처럼 벗어나기 어려운 공간이 무서워요", DisorderType.PANIC_DISORDER),

  // 수면장애 증상 (25-30)
  DIFFICULTY_FALLING_ASLEEP(25, "밤에 누워도 쉽게 잠들지 못하고 뒤척여요", DisorderType.SLEEP_DISORDER),
  FREQUENT_WAKING_DURING_SLEEP(26, "자다가 자주 깨고, 다시 잠들기 힘들어요", DisorderType.SLEEP_DISORDER),
  NOT_REFRESHED_AFTER_SLEEP(27, "아침에 일어나도 개운하지 않고 하루 종일 피곤해요", DisorderType.SLEEP_DISORDER),
  NIGHTMARES_OR_EXCESSIVE_MOVEMENT_DURING_SLEEP(
      28, "밤에 악몽을 꾸거나 몸이 심하게 움직여요", DisorderType.SLEEP_DISORDER),
  BREATHING_PROBLEMS_OR_SNORING_DURING_SLEEP(
      29, "자는 중에 숨이 막히거나 코를 심하게 곤다고 해요", DisorderType.SLEEP_DISORDER),
  REVERSED_DAY_NIGHT_RHYTHM(30, "낮과 밤이 뒤바뀌어 생활 리듬이 엉망이에요", DisorderType.SLEEP_DISORDER),

  // ADHD 증상 (31-38)
  PROCRASTINATION_AND_GUILT(31, "해야 할 일을 미루다가 죄책감에 시달릴 때가 많아요", DisorderType.ADHD),
  SEVERE_EMOTIONAL_FLUCTUATIONS(32, "감정 기복이 심해서 사소한 일에도 쉽게 욱하거나 울컥해요", DisorderType.ADHD),
  LOSING_ITEMS_AND_DIFFICULTY_ORGANIZING(33, "물건을 자주 잃어버리거나, 정리정돈이 정말 어려워요", DisorderType.ADHD),
  POOR_TIME_MANAGEMENT(34, "시간 감각이 흐릿해서 약속이나 일정을 자주 놓쳐요", DisorderType.ADHD),
  SELF_BLAME_FOR_MINOR_MISTAKES(35, "조금만 실수해도 '내가 왜 이럴까' 하며 자책해요", DisorderType.ADHD),
  DISTRACTED_DURING_CONVERSATIONS(36, "사람들과 있을 때 생각이 산만해 대화 흐름을 놓쳐요", DisorderType.ADHD),
  DIFFICULTY_FOCUSING_ON_ONE_TASK(37, "한 가지 일에 오래 집중하지 못하고 딴생각이 들어요", DisorderType.ADHD),
  APP_SWITCHING_AND_INCOMPLETE_TASKS(38, "앱을 켰다 껐다 반복하면서 일을 못 끝낼 때가 많아요", DisorderType.ADHD),

  // 강박장애 증상 (39-44)
  COMPULSIVE_CHECKING(
      39, "확인하지 않으면 큰일 날 것 같아 같은 걸 확인해요", DisorderType.OBSESSIVE_COMPULSIVE_DISORDER),
  INTRUSIVE_THOUGHTS_AND_IMAGES(
      40, "자꾸 떠오르는 생각이나 이미지 때문에 괴롭고 집중이 안 돼요", DisorderType.OBSESSIVE_COMPULSIVE_DISORDER),
  EXCESSIVE_WASHING_OR_SPECIFIC_ROUTINES(
      41, "손을 지나치게 자주 씻거나, 특정 순서를 지켜야 마음이 편해요", DisorderType.OBSESSIVE_COMPULSIVE_DISORDER),
  MUST_FOLLOW_SELF_MADE_RULES(
      42, "이상하다는 걸 알면서도 스스로 만든 규칙을 꼭 지켜야 해요", DisorderType.OBSESSIVE_COMPULSIVE_DISORDER),
  THOUGHTS_GET_WORSE_WHEN_TRYING_TO_STOP(
      43, "생각을 멈추려고 해도 더 심해져서 스트레스를 받아요", DisorderType.OBSESSIVE_COMPULSIVE_DISORDER),
  ANXIETY_WITHOUT_COMPULSIVE_BEHAVIOR(
      44, "강박 행동을 하지 않으면 불안하거나 큰일 날 것 같은 예감이 들어요", DisorderType.OBSESSIVE_COMPULSIVE_DISORDER),

  // PTSD 증상 (45-50)
  TRAUMATIC_MEMORY_FLASHBACKS(45, "특정 장면이나 상황에서 과거의 끔찍한 기억이 떠올라 무서워요", DisorderType.PTSD),
  RECURRING_NIGHTMARES_AND_FEAR_OF_SLEEP(46, "자꾸 같은 악몽을 꾸거나, 잠들기조차 무서울 때가 있어요", DisorderType.PTSD),
  TRIGGERED_BY_SIMILAR_SITUATIONS(47, "비슷한 장소나 사람, 소리만 들어도 갑자기 불안해져요", DisorderType.PTSD),
  EXAGGERATED_STARTLE_RESPONSE(48, "작은 소리나 접촉에도 과하게 놀라고 긴장해요", DisorderType.PTSD),
  DIFFICULTY_TRUSTING_PEOPLE(49, "사람을 쉽게 믿지 못하고, 누가 다가오면 먼저 의심하게 돼요", DisorderType.PTSD),
  EMOTIONAL_NUMBNESS_AND_LETHARGY(50, "예전 같지 않게 무기력하거나 감정이 무뎌진 느낌이 들어요", DisorderType.PTSD),

  // 배변배뇨 긴장 반응 증상 (51-56)
  SUDDEN_URGE_TO_URINATE_WHEN_ANXIOUS(
      51, "긴장하거나 불안할 때 갑자기 소변이 마려워져 참기 힘들어요", DisorderType.EXCRETORY_TENSION_RESPONSE),
  BOWEL_SENSITIVITY_IN_STRESSFUL_SITUATIONS(
      52, "시험, 발표, 사람 많은 곳에 가면 대변 신호에 더 예민해져요", DisorderType.EXCRETORY_TENSION_RESPONSE),
  WORRY_ABOUT_TOILETS_BEFORE_GOING_OUT(
      53, "외출 전이나 이동 중에 화장실이 걱정돼 활동을 망설여요", DisorderType.EXCRETORY_TENSION_RESPONSE),
  SUDDEN_ABDOMINAL_PAIN_WHEN_NERVOUS(
      54, "긴장하면 복통이나 설사가 갑자기 찾아와요", DisorderType.EXCRETORY_TENSION_RESPONSE),
  ANXIETY_ABOUT_TOILET_AVAILABILITY(
      55, "화장실을 찾기 어렵다고 느끼면 심장이 두근거리고 불안해져요", DisorderType.EXCRETORY_TENSION_RESPONSE),
  DAILY_LIFE_AFFECTED_BY_EXCRETORY_ISSUES(
      56, "배변·배뇨 문제가 반복돼 일상이나 대인관계가 불편해졌어요", DisorderType.EXCRETORY_TENSION_RESPONSE),

  // 섭식장애 증상 (57-62)
  BINGE_EATING_AND_SELF_CRITICISM(
      57, "한 번에 너무 많은 음식을 먹고, 나중에 스스로를 심하게 비난해요", DisorderType.EATING_DISORDER),
  INABILITY_TO_CONTROL_EATING(58, "먹는 걸 멈추고 싶은데 통제할 수 없을 때가 많아요", DisorderType.EATING_DISORDER),
  COMPENSATORY_BEHAVIORS_FOR_WEIGHT_GAIN(
      59, "살이 찔까 봐 걱정돼서 일부러 토하거나 과하게 운동해요", DisorderType.EATING_DISORDER),
  OBSESSION_WITH_CALORIES_AND_WEIGHT(60, "하루 종일 칼로리나 몸무게에 집착하게 돼요", DisorderType.EATING_DISORDER),
  DELIBERATE_STARVATION(61, "배가 고파도 일부러 안 먹거나 계속 굶게 돼요", DisorderType.EATING_DISORDER),
  DISTORTED_BODY_IMAGE(62, "내 몸이 실제보다 뚱뚱해 보이고, 만족한 적이 없어요", DisorderType.EATING_DISORDER),

  // 성기능장애 증상 (63-68)
  ANXIETY_ABOUT_SEXUAL_RELATIONSHIPS(
      63, "성관계를 해야 한다는 생각만 해도 불안하거나 부담스러워요", DisorderType.SEXUAL_DYSFUNCTION),
  ABNORMAL_SEXUAL_DESIRE(
      64, "성적인 욕구가 거의 없거나, 반대로 너무 강해서 스스로도 힘들어요", DisorderType.SEXUAL_DYSFUNCTION),
  DIFFICULTY_WITH_SEXUAL_AROUSAL(65, "성관계 중 흥분이 잘 안 되거나 유지가 안 돼요", DisorderType.SEXUAL_DYSFUNCTION),
  DIFFICULTY_REACHING_ORGASM(66, "오르가즘에 도달하지 못하거나 시간이 너무 오래 걸려요", DisorderType.SEXUAL_DYSFUNCTION),
  LOW_SELF_ESTEEM_DUE_TO_SEXUAL_PROBLEMS(
      67, "성적인 문제로 인해 자존감이 낮아지고 위축됐어요", DisorderType.SEXUAL_DYSFUNCTION),
  RELATIONSHIP_CONFLICTS_DUE_TO_SEXUAL_ISSUES(
      68, "성적인 문제 때문에 파트너와 갈등이 자주 생겨요", DisorderType.SEXUAL_DYSFUNCTION);

  private final int id;
  private final String description;
  private final DisorderType disorderType;

  public static SymptomType fromId(int id) {
    return Arrays.stream(values())
        .filter(symptom -> symptom.id == id)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid symptom id: " + id));
  }

  public static Set<SymptomType> fromIds(List<Integer> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }
    return ids.stream().map(SymptomType::fromId).collect(Collectors.toSet());
  }

  public static List<Integer> toIds(Set<SymptomType> symptoms) {
    if (symptoms == null || symptoms.isEmpty()) {
      return List.of();
    }
    return symptoms.stream().map(SymptomType::getId).sorted().collect(Collectors.toList());
  }

  public static Set<SymptomType> getByDisorderType(DisorderType disorderType) {
    return Arrays.stream(values())
        .filter(symptom -> symptom.disorderType == disorderType)
        .collect(Collectors.toSet());
  }

  public static Set<SymptomType> getByDisorderTypes(Set<DisorderType> disorderTypes) {
    if (disorderTypes == null || disorderTypes.isEmpty()) {
      return Set.of();
    }
    return Arrays.stream(values())
        .filter(symptom -> disorderTypes.contains(symptom.disorderType))
        .collect(Collectors.toSet());
  }
}
