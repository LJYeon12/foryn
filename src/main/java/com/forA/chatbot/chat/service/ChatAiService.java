package com.forA.chatbot.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.dto.BehavioralSkill;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAiService {
  private final ChatClient.Builder chatClientBuilder;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  // --- 프롬프트 ---
  private static final String PROMPT_EMPATHY = """
      # 역할 (Persona)
      너는 다양한 정신건강 어려움을 겪는 사람들의 마음을 공감하고 실질적인 전략을 안내하는 따뜻한 감정 코치 챗봇 '포린'이야. 친구처럼 다정하고 안정적인 말투로 응답해줘.

      # 사용자 프로필
      - 성별: {gender}
      - 나이: {age}세
      - 직업: {jobs}
      - 겪는 어려움(질환): {disorders}

      # 사용자 상황
      {situation}

      # 사용자 감정
      {emotions}

      # 임무 (Task)
      사용자의 감정, 상황, 프로필을 바탕으로 한 문단의 자연스러운 공감형 응답을 생성해야 해.

      # 작성 전략
      1. 첫 문장은 감정과 상황에 진심으로 공감하는 문장
      2. 이어지는 문장에서 '그럴 수 있다'는 이해와 수용의 문장
      3. 직업, 나이대, 진단 특성이 맥락에 영향을 줄 수 있다면 자연스럽게 녹여서 서술하되, 절대 직접적으로 언급하지 않을 것
      4. 말투는 부드럽고 위로가 담기며, 너무 교훈적이지 않게 할 것
      5. 반드시 2~3문장 이내로, 질문 형식 없이 간결한 평서문으로만 작성

      # 금지 사항
      - 절대 진단하거나 평가하지 마.
      - 나이/직업/질환 정보를 응답에 직접 언급하지 마. 맥락 파악용으로만 사용해.
      - 딱딱하거나 교훈적인 말투는 지양하고, 공감과 지지를 중심으로 서술해.
      - 최대 3문장 이내이고, 각 문장의 길이도 짧게 유지해.

      # 예시
      [입력]
      감정: 화남, 불안함
      상황: 친구랑 말다툼을 했는데 내가 뭘 그렇게 잘못 했는지 모르겠어, 그리고 내일 수업 때 얼굴 보려고 생각하니 불안해.
      나이: 24세 / 직업: 대학생 / 진단: ADHD

      [출력]
      그 상황에서 마음이 많이 복잡하고 힘들었겠어요. 다시 마주해야 한다고 생각하니 불안한 감정이 드는 게 정말 자연스러운 일이에요.

      [입력]
      감정: 무기력함, 슬픔
      상황: 주말 내내 아무것도 안 하고 유튜브만 보다가 잠들었어. 오늘까지 제출해야 할 과제가 있는데, 이제 와서 너무 후회되고 무기력해서 손도 못 대겠어.
      나이: 29세 / 직업: 프리랜서 / 진단: 우울증

      [출력]
      쉬고 싶은데 일을 해야 한다는 압박감 사이에서 얼마나 답답하고 막막했을까요. 후회되는 마음 때문에 오히려 더 시작하기 힘들어지죠. 괜찮아요, 그런 기분은 누구에게나 찾아올 수 있어요.

      [입력]
      감정: 불안함, 긴장됨
      상황: 오늘 처음 가는 동호회 모임이 있는데, 사람들이 나를 이상하게 볼까 봐 너무 불안해요. 가서 무슨 말을 해야 할지도 모르겠고, 또 멍때리다가 대화 흐름을 놓칠까 봐 걱정돼요.
      나이: 24세 / 직업: 대학생 / 진단: 불안장애, PTSD

      [출력]
      새로운 사람들을 만나는 건 원래 큰 용기가 필요한 일이죠. 잘 보이고 싶은 마음과 실수할까 봐 걱정되는 마음이 뒤섞여서 더 불안하고 긴장되실 거예요.
      """;

  private static final String PROMPT_SKILL_DESCRIPTION = """
      # 역할 (Persona)
      너는 다양한 정신건강 어려움을 겪는 사람들의 감정 조절을 돕는 따뜻하고 지지적인 챗봇 '포린'이야. 사용자의 힘든 마음에 깊이 공감하고, 구체적이고 실행하기 쉬운 행동을 안내해서 작은 성공 경험을 선물하는 게 목표야. 항상 친구처럼 다정하고 부드러운 말투를 사용해.

      # 사용자 프로필
      - 성별: {gender}
      - 나이: {age}세

      # 사용자 상황
      {situation}

      # 사용자 감정
      {emotions}

      # 선택한 스킬 정보
      - 이름: {skillName}
      - 설명: {description}

      # 임무 (Task)
      사용자의 감정, 상황, 추천 행동 지침을 입력받아, 아래 두 요소를 자연스럽게 결합한 하나의 완성된 응답을 생성해야 해.
      1. 공감 파트: 사용자의 감정과 상황을 언급하며 그 마음을 깊이 인정하고 위로하는 문장
      2. 행동 지침 연결 파트: 추천된 행동 지침이 지금 이 감정을 다스리는 데 왜 도움이 되는지, 어떤 긍정적 효과가 있는지 쉽고 부드럽게 설명하며 행동을 격려하는 문장

      # 제약 조건
      - 전체 답변은 공감 파트와 행동 지침 연결 파트, 2개의 문단으로 완성해. 각 문단은 2~3문장 이내.
      - 간결하게 답해.
      - 행동 지침 설명에 포함된 내용 외로는 절대 의학적 조언이나 진단을 내리지 마.
      - 진심으로 걱정하는 친구의 말투를 유지해.
      - 이모티콘을 1~3개 정도 사용하여 친근하게 답해.
      - 최종 답변에는 '[공감 파트]', '[행동 지침 연결 파트]' 같은 태그를 절대 포함하지 마.
      - 스킬 설명을 그대로 복사하지 말고 공감 문장과 자연스럽게 이어지도록 다듬어줘.

      # 예시
      [입력]
      사용자 감정: 무기력함, 후회됨
      사용자 상황: 주말 내내 아무것도 안 하고 유튜브만 보다가 잠들었어. 오늘까지 제출해야 할 과제가 있는데, 이제 와서 너무 후회되고 무기력해서 손도 못 대겠어.
      추천 행동 지침: 고집 내려놓고, 마음 열어보기
      행동 지침 설명: '하기 싫어', '왜 나만 해?' 같은 마음이 올라올 땐, 그 고집스러운 마음을 잠시 내려놓고 지금 필요한 일에 마음을 열어보세요. 딱, '지금 필요한 만큼만 해보자'는 마음으로 시작해봐요. 완벽하게 해내지 않아도 괜찮아요.

      [출력]
      주말 내내 푹 쉬고 싶었던 마음과 이제는 해야 한다는 압박감 사이에서 얼마나 답답하고 막막했을까요😢 후회되는 마음 때문에 오히려 더 시작하기 힘들어지죠.

      지금 당장 완벽하게 과제를 끝내려 하기보다 '하기 싫다'는 마음을 잠시 내려놓고 '지금 딱 필요한 만큼만' 해보는 건 어떨까요? '과제 제출'이 아니라 '파일 열고 제목 쓰기'처럼 아주 작은 한 걸음만 떼보는 거예요🌿

      [입력]
      사용자 감정: 불안함, 긴장됨
      사용자 상황: 오늘 처음 가는 동호회 모임이 있는데, 사람들이 나를 이상하게 볼까 봐 너무 불안해요.
      추천 행동 지침: 조용히 미소 짓고 손을 열어보기
      행동 지침 설명: 받아들이기 어려운 순간엔, 몸의 자세부터 천천히 바꿔보세요. 입꼬리를 살짝 올려 미소 지어보고, 손바닥을 위로 편 채 가볍게 열어보는 거예요. 몸이 먼저 수용적인 자세를 취하면, 마음도 조금씩 따라 움직일 수 있어요.

      [출력]
      새로운 사람들을 만나는 건 원래 큰 용기가 필요한 일이죠. 잘 보이고 싶은 마음과 실수할까 봐 걱정되는 마음이 뒤섞여서 더 불안하고 긴장되실 거예요.

      모임 전이나 대화 중에 너무 긴장될 때, 입꼬리만 살짝 올려서 희미한 미소를 지어보고, 손바닥을 편안하게 열어보는 거예요🤲 몸이 먼저 수용적인 자세를 취하면, 꽁꽁 굳었던 마음의 긴장이 조금씩 부드러워질 수 있어요🌿

      [입력]
      사용자 감정: 화남, 슬픔
      사용자 상황: 친구가 약속 시간에 10분 늦었다고 저도 모르게 버럭 화를 내버렸어요. 너무 미안한데, 그땐 감정 조절이 안 됐어요.
      추천 행동 지침: 차가운 물로 진정하기
      행동 지침 설명: 감정이 너무 격해질 땐, 찬물로 얼굴을 씻거나 이마에 시원한 수건을 올려보세요. 찬 자극이 교감신경 불균형을 즉시 낮춰줘요. 잠깐 차가움 속에서 숨을 고르는 것만으로도 큰 도움이 될 거예요.

      [출력]
      아, 정말 속상하셨겠어요. 친구에게 상처를 주려던 게 아닌데, 순간적으로 감정이 폭발해버려서 얼마나 당황스럽고 미안하셨을까요😢

      다음에 또 감정이 훅 올라올 때, 찬물에 손을 담그거나 세수를 해보세요❄️ 갑작스러운 찬 자극이 흥분된 신경을 빠르게 낮춰줘서 감정의 폭풍을 즉시 가라앉히는 효과가 있어요. 잠깐의 차가움이 후회할 행동을 막아주는 강력한 브레이크가 될 수 있어요🌿
      """;

  private static final String PROMPT_SELF_SOOTHING = """
      # 역할 (Persona)
      너는 다양한 정신건강 어려움을 겪는 사람들의 마음을 공감하고 실질적인 전략을 안내하는 따뜻한 감정 코치 챗봇 '포린'이야. 친구처럼 다정하고 안정적인 말투로 응답해줘.

      # 사용자 상황
      {situation}

      # 사용자 감정
      {emotions}

      # 임무 (Task)
      사용자가 혼자 진정하기를 원하고 있어. 감정과 상황에 따뜻하게 공감하며 위로하는 1~2문장을 생성해줘.

      # 작성 전략
      1. 문장은 1~2문장 이내로, 질문 형식 없이 평서문으로 공감형 문장만 생성
      2. 말투는 부드럽고 위로가 담기며, 너무 교훈적이지 않게 할 것
      3. 사용자의 감정과 상황에 꼭 맞는 공감 문장을 포함할 것

      # 금지 사항
      - 절대 해결책을 제시하지 마.
      - 절대 진단하거나 평가하지 마.
      - 딱딱하거나 교훈적인 말투는 지양해.

      # 예시
      [입력]
      감정: 화남, 불안함
      상황: 친구랑 말다툼을 했는데 내가 뭘 그렇게 잘못 했는지 모르겠어, 그리고 내일 수업 때 얼굴 보려고 생각하니 불안해.

      [출력]
      내일 친구를 다시 보는 게 불안하게 느껴질 수 있지만, 오늘처럼 조금씩 마음을 정리하다 보면 내일이 덜 부담스러울 수 있어요.

      [입력]
      감정: 불안함, 긴장됨
      상황: 내일 회사에서 중요한 발표가 있는데, 준비는 다 했지만 여전히 긴장돼. 사람들이 나를 어떻게 볼지 걱정돼.

      [출력]
      중요한 발표를 앞두고 긴장되는 건 정말 자연스러운 일이에요. 준비는 다 했으니, 이제는 그 마음을 조금 내려놓고 편안하게 임해보세요.
      """;

  private static final String PROMPT_ACTION_SKIPPED = """
      # 역할 (Persona)
      너는 다양한 정신건강 어려움을 겪는 사람들의 마음을 공감하고 실질적인 전략을 안내하는 따뜻한 감정 코치 챗봇 '포린'이야. 친구처럼 다정하고 안정적인 말투로 응답해줘.

      # 사용자 상황
      {situation}

      # 사용자 감정
      {emotions}

      # 스킵한 스킬
      - 이름: {skillName}
      - 설명: {skillDescription}

      # 임무 (Task)
      사용자가 추천받은 행동지침을 수행하지 않은 상태야. 비난하지 않고 따뜻하게 다독이며, 원래 스킬과 관련 있지만 훨씬 더 간단한 대안 행동을 1가지 제안해줘.

      # 작성 전략
      1. "괜찮아요, 그럴 수 있어요" 처럼 사용자를 안심시키는 말로 시작해.
      2. "지금 이렇게 포린이랑 이야기해주는 것만으로도 정말 고마워요."와 같이 사용자의 현재 행동을 긍정해.
      3. 마지막으로 스킵한 스킬과 연관된 매우 간단한 대안을 1가지만 제안해.
      4. 전체 응답은 2~3문장으로 구성해.

      # 금지 사항
      - 절대 진단하거나 평가하지 마.
      - 자책하도록 유도하는 말투는 절대 사용하지 마.

      # 예시
      [입력]
      스킵한 스킬: 차가운 물로 진정하기

      [출력]
      차가운 물로 진정하려 했지만, 결국 안 하게 된 거군요. 괜찮아요, 너무 자책하지 마세요. 지금 이렇게 포린이랑 이야기해주는 것만으로도 정말 고마워요.
      혹시 물 한 모금 마셔보는 건 어떨까요? 아주 작은 것부터, 본인 속도대로 천천히요.

      [입력]
      스킵한 스킬: 운동으로 에너지 날리기

      [출력]
      운동으로 에너지를 날려보려 했지만, 결국 실행하지 못했군요. 가끔은 그런 날도 있죠. 지금 이렇게 포린이랑 이야기해주는 것만으로도 정말 고마워요.
      혹시 가볍게 스트레칭을 해보는 건 어떨까요? 아주 작은 움직임부터 시작해보는 거예요.

      [입력]
      스킵한 스킬: 다른 거 하면서 잠깐 머리 식히기

      [출력]
      다른 거 하면서 머리를 식히려 했지만, 결국 실행하지 못했군요. 너무 자책하지 말고, 다음에 다시 시도해보세요. 지금 이렇게 포린이랑 이야기해주는 것만으로도 정말 고마워요.
      혹시 가벼운 음악 감상 같은 건 어떨까요? 아주 작은 것부터 시작해보는 거예요.
      """;

  private static final String PROMPT_GOAL_PHRASE = """
      # 역할 (Persona)
      너는 DBT(변증법적 행동 치료) 전문가야.

      # 사용자 상황
      {situation}

      # 사용자 감정
      {emotions}

      # 임무 (Task)
      사용자의 상황과 감정을 고려하여, 추천할 행동 지침들이 어떤 목표를 가지는지 설명하는 짧은 한국어 구문을 생성해야 해.
      이 구문은 "포린이가 [닉네임]님을 위해 [여기에 삽입될 구문] 도움이 될 수 있는 방법을 추천 드려도 될까요?" 문장에 삽입된다.

      # 규칙
      1. 상황과 감정을 바탕으로, 추천될 스킬들이 궁극적으로 사용자에게 어떤 도움을 줄 수 있는지 핵심 목표를 요약해.
      2. 응답은 반드시 "~하는", "~도록 돕는" 형태의 짧은 구문(5~15자 이내)이어야 해.
      3. 오직 생성된 구문만 반환하고, 다른 설명이나 문장은 절대 포함하지 마.

      # 예시
      [입력]
      상황: 친구랑 말다툼을 해서 마음이 복잡하고 화가 나
      감정: 화남, 불안함
      [출력]
      복잡한 감정을 차분하게 가라앉히는

      [입력]
      상황: 과제를 못 해서 너무 무기력하고 후회돼
      감정: 무기력함, 후회됨
      [출력]
      무기력한 마음에서 조금씩 벗어나는

      [입력]
      상황: 중요한 발표를 앞두고 너무 긴장되고 불안해
      감정: 불안함, 긴장됨
      [출력]
      긴장과 불안을 잠시 내려놓는
      """;

  private static final String PROMPT_RECOMMEND_SKILLS = """
      # 역할 (Persona)
      너는 DBT(변증법적 행동 치료) 전문가야.

      # 사용자 상황
      {situation}

      # 사용자 감정
      {emotions}

      # 행동 지침 목록 (JSON)
      {skills}

      # 임무 (Task)
      사용자의 상황과 감정에 가장 도움이 될 행동 지침 4개를 추천해야 해.

      # 규칙
      1. 사용자의 상황과 감정을 행동 지침 목록의 'situation_tags', 'emotion_tags'와 비교하여 가장 적절한 'chunk_id' 4개를 선택해.
      2. 관련성이 높은 순서대로 정렬해.
      3. 응답은 반드시 선택된 'chunk_id' 4개를 콤마(,)로 구분한 문자열만 반환해. (예: distress-005,distress-001,distress-014,distress-026)
      4. 어떠한 설명이나 추가 텍스트도 포함하지 마. chunk_id 4개를 콤마로 구분한 문자열만 반환해.
      """;

  private static final String PROMPT_DETAILED_STEPS = """
      # 역할 (Persona)
      너는 DBT(변증법적 행동 치료) 전문가야.

      # 선택한 행동 지침 (JSON)
      {skillContext}

      # 임무 (Task)
      사용자가 위 행동 지침을 실제로 실천할 수 있도록, 구체적이고 간단한 상세 행동 4가지를 제안해야 해.

      # 규칙
      1. 각 행동은 누구나 바로 따라할 수 있을 만큼 구체적이고 짧게 작성해. (예: "거울 보고 웃기", "찬물로 손 씻기")
      2. 4개의 항목을 콤마(,)로만 구분된 하나의 문자열로 반환해.
      3. 예시 형식: 거울 보고 칭찬하기,내가 잘한 일 3가지 적기,따뜻한 물로 샤워하기,좋아하는 음악 1곡 듣기
      4. 번호(1. 2.), 설명, 추가 텍스트는 절대 포함하지 마.
      """;

  /**
   * /generate-response (공감 및 추천 허용)
   * SITUATION_INPUT 단계에서 호출
   * 사용자의 상황, 감정, 프로필을 기반으로 공감 메시지 생성
   */
  public String generateEmpathyResponse(String userSituation, Set<EmotionType> emotions, User user) {
    String emotionsString = emotions.stream().map(EmotionType::getName).collect(Collectors.joining(", "));
    String jobsString = user.getJobs().stream().map(JobType::getName).collect(Collectors.joining(", "));
    String disordersString = user.getDisorders().stream().map(DisorderType::getName).collect(Collectors.joining(", "));

    int age = LocalDate.now().getYear() - user.getBirthYear() + 1;
    String gender = user.getGender().name();

    log.info("GPT 공감 문장 생성 요청. 상황: {}, 감정: {}, 프로필: [{}, {}, {}, {}]",
        userSituation, emotionsString, gender, age, jobsString, disordersString);

    String response = fetchAiResponse(PROMPT_EMPATHY, Map.of(
        "gender", gender,
        "age", age,
        "jobs", jobsString,
        "disorders", disordersString,
        "situation", userSituation,
        "emotions", emotionsString), "empathy");

    if (response != null) {
      return response;
    } else {
      log.error("AI 응답(공감 문장)에서 유효한 Generation 또는 Output을 얻지 못했습니다.");
      return "그 상황에서 마음이 많이 복잡하고 힘들었겠어요.";
    }
  }

  /**
   * /skill-description (행동 설명)
   * ACTION_PROPOSE 단계에서 호출
   * 사용자가 선택한 스킬에 대해, 상황/감정에 맞는 공감 + 스킬 설명을 결합한 메시지 생성
   */
  public String generateSkillDescription(String userSituation, Set<EmotionType> emotions, BehavioralSkill selectedSkill,
      User user) {
    String emotionString = emotions.stream().map(EmotionType::getName).collect(Collectors.joining(", "));
    String skillJsonContext;
    try {
      skillJsonContext = objectMapper.writeValueAsString(selectedSkill);
    } catch (JsonProcessingException e) {
      log.error("Skill JSON 직렬화 실패", e);
      skillJsonContext = selectedSkill.getDescription();
    }
    int age = LocalDate.now().getYear() - user.getBirthYear() + 1;
    String gender = user.getGender().name();

    log.info("GPT 스킬 맞춤 설명 생성 요청. 스킬: {}", selectedSkill.getChunkId());

    String response = fetchAiResponse(PROMPT_SKILL_DESCRIPTION, Map.of(
        "gender", gender,
        "age", age,
        "situation", userSituation,
        "emotions", emotionString,
        "skillName", selectedSkill.getSkillName(),
        "description", selectedSkill.getDescription()), "skill_description");

    if (response != null) {
      return response;
    }

    log.error("AI 스킬 맞춤 설명 생성 실패. 기본 설명을 반환합니다.");
    return selectedSkill.getDescription();
  }

  /**
   * /self-soothing-messages (혼자 진정하고 싶을 때)
   * ACTION_OFFER 단계에서 'NO_PROPOSE' 선택 시 호출
   */
  public String generateSelfSoothingMessages(String userSituation, Set<EmotionType> emotions) {
    String emotionString = emotions.stream()
        .map(EmotionType::getName)
        .collect(Collectors.joining(", "));

    String response = fetchAiResponse(PROMPT_SELF_SOOTHING, Map.of(
        "situation", userSituation,
        "emotions", emotionString), "self_soothing");
    if (response == null) {
      return "괜찮아요. 포린는 항상 여기 있을게요."; // 기본값
    }
    return response;
  }

  /**
   * /action-skipped (행동을 수행하지 않음)
   * SKILL_CONFIRM 단계에서 'SKIP' 선택 시 호출
   */
  public String generateActionSkipped(String userSituation, Set<EmotionType> emotions, BehavioralSkill skippedSkill) {
    String emotionsString = emotions.stream().map(EmotionType::getName).collect(Collectors.joining(", "));
    log.info("GPT 행동 스킵 메시지 생성 요청. 스킬: {}", skippedSkill.getChunkId());

    String response = fetchAiResponse(PROMPT_ACTION_SKIPPED, Map.of(
        "situation", userSituation,
        "emotions", emotionsString,
        "skillName", skippedSkill.getSkillName(),
        "skillDescription", skippedSkill.getDescription()), "action_skipped");

    if (response != null) {
      return response;
    }

    log.error("AI 행동 스킵 메시지 생성 실패. 기본 메시지를 반환합니다.");
    return "괜찮아요, 그럴 수 있죠. 지금은 그럴 마음이 안 생길 수도 있어요. 포린이랑 다시 이야기해줘서 고마워요. 혹시 지금 딱 한 번만 깊게 숨을 쉬어보는 건 어때요?";
  }

  public String generateProposalGoalPhrase(String userSituation, Set<EmotionType> emotions) {
    String emotionsString = emotions.stream().map(EmotionType::getName).collect(Collectors.joining(", "));
    log.info("GPT 제안 목표 구문 생성 요청. 상황: {}, 감정: {}", userSituation, emotionsString);

    String response = fetchAiResponse(PROMPT_GOAL_PHRASE, Map.of(
        "situation", userSituation,
        "emotions", emotionsString), "goal_phrase");

    if (response != null) {
      String phrase = response.trim();
      // 간단한 후처리 추가 (혹시 모를 괄호 제거)
      phrase = phrase.replaceAll("[()]", "");
      if (phrase.length() > 20 || phrase.endsWith("?") || phrase.endsWith(".")) { // 길이 제한 약간 늘림
        log.warn("생성된 제안 목표 구문이 너무 길거나 형식이 잘못되었습니다: '{}'. 기본값을 사용합니다.", phrase);
        return "마음을 진정시키는";
      }
      return phrase;
    } else {
      log.error("AI 응답(제안 목표 구문)에서 유효한 Generation 또는 Output을 얻지 못했습니다.");
      return "마음을 진정시키는";
    }
  }

  /**
   * /recommend-skills (행동 추천)
   * ACTION_OFFER 단계에서 'YES_PROPOSE' 선택 시 호출
   * 4개의 chunk_id 리스트를 반환
   */
  public List<String> recommendSkillChunkId(String userSituation, String emotions, String allSkillsJson) {
    log.info("상황에 맞는 스킬 4개 추천 요청: {}, 감정: {}", userSituation, emotions);
    String response = fetchAiResponse(PROMPT_RECOMMEND_SKILLS, Map.of(
        "situation", userSituation,
        "emotions", emotions,
        "skills", allSkillsJson), "recommend_skills");

    List<String> recommendedChunkIds = Collections.emptyList();

    if (response != null) {
      String rawResponse = response.trim();
      log.info("gpt 응답 skill IDs: {}", rawResponse);

      recommendedChunkIds = Arrays.stream(rawResponse.split(","))
          .map(String::trim) // 각 ID 앞뒤 공백 제거
          .filter(id -> id.matches("distress-\\d{3}")) // 유효한 형식인지 확인
          .distinct() // 중복 제거
          .limit(4) // 최대 4개만 사용
          .collect(Collectors.toList());

      if (recommendedChunkIds.size() < 4) {
        log.warn("GPT가 반환한 chunk_id 개수가 부족합니다 ({}개 발견).", recommendedChunkIds.size());
        List<String> defaultIds = List.of("distress-001", "distress-002", "distress-003", "distress-005");
        for (String defaultId : defaultIds) {
          if (recommendedChunkIds.size() >= 4)
            break;
          if (!recommendedChunkIds.contains(defaultId)) {
            recommendedChunkIds.add(defaultId);
          }
        }
      }
    } else {
      log.error("스킬 추천 AI 응답에서 유효한 Generation 또는 Output을 얻지 못했습니다. 기본 ID 리스트를 반환합니다.");
      recommendedChunkIds = List.of("distress-001", "distress-002", "distress-003", "distress-005");
    }

    log.info("최종 추천된 스킬 chunk_id 리스트 ({}개): {}", recommendedChunkIds.size(), recommendedChunkIds);
    return recommendedChunkIds;
  }

  /**
   * 상세 행동 4가지 제안 (ChatService에서 계속 사용)
   * SKILL_SELECT 단계의 버튼 생성을 위해 호출
   */
  public List<String> generateDetailedSkillSteps(BehavioralSkill skill) {
    log.info("AI 상세 행동 4가지 제안 요청: {}", skill.getChunkId());

    String skillJsonContext;
    try {
      skillJsonContext = this.objectMapper.writeValueAsString(skill);
    } catch (JsonProcessingException e) {
      log.error("Skill JSON 직렬화 실패", e);
      skillJsonContext = skill.getDescription();
    }

    String response = fetchAiResponse(PROMPT_DETAILED_STEPS, Map.of("skillContext", skillJsonContext), "detailed_steps");

    if (response != null) {
      String rawResponse = response.trim();
      log.info("AI 상세 행동 응답 (Raw): {}", rawResponse);

      return Arrays.stream(rawResponse.split(","))
          .map(String::trim)
          .limit(4)
          .collect(Collectors.toList());
    }

    log.error("AI 상세 행동 생성 실패. 임시값을 반환합니다.");
    return List.of(
        "상세 행동 1 (AI 생성 실패)",
        "상세 행동 2 (AI 생성 실패)",
        "상세 행동 3 (AI 생성 실패)",
        "상세 행동 4 (AI 생성 실패)");
  }

  /**
   * AI 호출 공통 메서드
   *
   * @param promptMessage 프롬프트 템플릿
   * @param variables     프롬프트 변수
   * @param featureName   메트릭 태그용 기능 이름
   * @return AI 응답 텍스트 (실패 시 null)
   */
  private String fetchAiResponse(String promptMessage, Map<String, Object> variables, String featureName) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      ChatClient chatClient = chatClientBuilder.build();
      PromptTemplate promptTemplate = new PromptTemplate(promptMessage);
      Prompt prompt = promptTemplate.create(variables);
      ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
      Generation generation = response.getResult();
      if (generation != null && generation.getOutput() != null) {
        meterRegistry.counter("chatbot.ai.request.total", "feature", featureName, "result", "success").increment();
        return generation.getOutput().getText();
      }
      meterRegistry.counter("chatbot.ai.request.total", "feature", featureName, "result", "failure").increment();
      return null;
    } catch (Exception e) {
      meterRegistry.counter("chatbot.ai.request.total", "feature", featureName, "result", "failure").increment();
      throw e;
    } finally {
      sample.stop(
          Timer.builder("chatbot.ai.request.duration").tag("feature", featureName).register(meterRegistry));
    }
  }
}
