package com.forA.chatbot.chat.component.impl;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.EmotionType;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEndStepHandler implements ChatStepHandler {

    private final ChatSessionRepository chatSessionRepository;

    @Override
    public String getStepName() {
        return ChatStep.CHAT_END.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        log.info("채팅 세션 {}은(는) 이미 종료되었습니다.", session.getId());
        return ChatStep.CHAT_END.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {
        String endReason = session.getTemporaryData("endReason");
        String nickname = user.getNickname() != null ? user.getNickname() : "USER";
        String content;

        String emotionsStr = session.getTemporaryData("selectedEmotions");
        Set<EmotionType> emotions = new HashSet<>();
        if (emotionsStr != null && !emotionsStr.isEmpty()) {
            emotions = Arrays.stream(emotionsStr.split(","))
                    .map(String::trim)
                    .map(name -> Arrays.stream(EmotionType.values())
                            .filter(e -> e.name().equals(name))
                            .findFirst()
                            .orElseThrow(() -> new ChatHandler(ErrorStatus.INVALID_EMOTION_NAME)))
                    .collect(Collectors.toSet());
        }

        boolean isPositiveFlow = "POSITIVE_EMOTION".equals(endReason);
        if (!isPositiveFlow && endReason == null && !emotions.isEmpty()) {
            isPositiveFlow = emotions.stream().allMatch(e -> e.getState() == EmotionType.EmotionState.POSITIVE);
        }

        if (isPositiveFlow) {
            content = createPositiveResponseMessage(emotions);
        } else if ("COMFORT_ALONE".equals(endReason) || "SKIPPED".equals(endReason)) {
            String comfortMessage = session.getTemporaryData("comfortMessage");
            if (comfortMessage == null) {
                comfortMessage = "괜찮아요. 포린은 항상 여기 있을게요.";
            }
            content = "알겠어요. 지금은 혼자 생각을 정리하고 싶은 마음이 클 수도 있겠네요. 괜찮아요, 꼭 바로 뭔가 해결하려고 하지 않아도 돼요. "
                    + comfortMessage + " "
                    + "필요할 때 언제든 말 걸어줘요. 포린은 항상 " + nickname + "님 편이에요.";

        } else if ("FEEDBACK_DONE".equals(endReason)) {
            boolean isFirstChat = chatSessionRepository.countByUserId(user.getId()) <= 1;
            if (isFirstChat) {
                content = "힘들 때마다 언제든 포린을 찾아주세요."
                        + "포린이 " + nickname + "님의 곁에서 도움이 될 수 있도록 함께할게요."
                        + "지금 약 페이지로 이동하면, 제 시간에 약을 복용하실 수 있도록 도와드릴게요!";
            } else {
                content = "힘들 때마다 언제든 포린을 찾아주세요."
                        + "포린이 " + nickname + "님의 곁에서 도움이 될 수 있도록 함께할게요.";
            }
        } else {
            content = "대화가 종료되었습니다.";
        }
        return ChatBotMessage.builder()
                .content(content)
                .type(MessageType.TEXT)
                .build();
    }

    private String createPositiveResponseMessage(Set<EmotionType> emotions) {
        if (emotions.size() == 1) {
            EmotionType emotion = emotions.iterator().next();
            switch (emotion) {
                case EXCITED: return "무언가 기대되는 일이 있었나 봐요! 그 에너지, 좋아요 😆";
                case JOY: return "즐거운 순간이 있었군요. 그 기분 오래오래 간직해요 😊";
                case PROUD: return "오늘 스스로에게 칭찬해줄 일이 있었나 봐요! 정말 잘했어요 👏";
                case HAPPY: return "행복하다고 느껴지는 순간, 너무 소중하죠. 지금 이 마음을 기억해요 💛";
                case FLUTTER: return "마음이 간질간질, 좋은 일이 기다리고 있나 봐요! 설렘은 삶의 활력소예요 🌸";
                case SO_SO: return "큰 감정 변화는 없지만, 이런 날도 충분히 괜찮아요. 그냥 있는 그대로의 하루도 소중해요 🍃";
                default: return "긍정적인 감정을 느끼셨군요! 좋아요.";
            }
        } else if (emotions.size() == 2) {
            Map<Set<EmotionType>, String> combinationMessages = Map.ofEntries(
                    Map.entry(Set.of(EmotionType.EXCITED, EmotionType.JOY), "신나고 즐거운 하루였네요! 이런 기분이 오래오래 이어졌으면 좋겠네요. 😄🎉"),
                    Map.entry(Set.of(EmotionType.EXCITED, EmotionType.PROUD), "신나고 뿌듯한 하루를 보내셨네요. 오늘의 성취가 포린도 뿌듯하게 느껴지네요. 😆👏"),
                    Map.entry(Set.of(EmotionType.EXCITED, EmotionType.HAPPY), "신나고 행복한 하루였네요. 좋은 일이 가득해서 저도 기분이 좋아지네요. 😊💛"),
                    Map.entry(Set.of(EmotionType.EXCITED, EmotionType.FLUTTER), "신나고 설레는 하루였네요. 앞으로도 기대되는 일이 많으시길 바랄게요. 💫🌸"),
                    Map.entry(Set.of(EmotionType.EXCITED, EmotionType.SO_SO), "신나는 순간도 있었고, 평범한 시간도 있었네요. 여러 감정이 어우러진 하루였던 것 같네요.🎭"),
                    Map.entry(Set.of(EmotionType.JOY, EmotionType.PROUD), "즐겁고 뿌듯한 하루를 보내셨네요. 오늘의 좋은 기억이 오래 남았으면 해요. 😊👏"),
                    Map.entry(Set.of(EmotionType.JOY, EmotionType.HAPPY), "즐거움과 행복이 함께한 하루였네요. 저도 덩달아 미소가 지어지네요. 😄💛"),
                    Map.entry(Set.of(EmotionType.JOY, EmotionType.FLUTTER), "즐겁고 설레는 하루였네요. 새로운 시작이나 만남이 있었던 걸까요? 앞으로도 좋은 일이 가득하길 바랄게요. 🌟😊"),
                    Map.entry(Set.of(EmotionType.JOY, EmotionType.SO_SO), "즐거운 순간도 있었고, 평범한 시간도 있었네요. 그런 하루도 충분히 의미 있네요. 🍃🙂"),
                    Map.entry(Set.of(EmotionType.PROUD, EmotionType.HAPPY), "뿌듯함과 행복이 함께한 하루였네요. 오늘의 성취가 큰 기쁨이 되었겠어요. 👏😊"),
                    Map.entry(Set.of(EmotionType.PROUD, EmotionType.FLUTTER), "뿌듯하고 설레는 하루였네요. 앞으로도 좋은 변화가 이어지길 바랄게요. 🌱💫"),
                    Map.entry(Set.of(EmotionType.PROUD, EmotionType.SO_SO), "뿌듯한 순간과 평범한 시간이 함께한 하루였네요. 그런 균형이 참 소중하네요. ⚖️🍀"),
                    Map.entry(Set.of(EmotionType.HAPPY, EmotionType.FLUTTER), "행복하고 설레는 하루였네요. 좋은 일이 곧 찾아올 것 같은 느낌이네요. 💛🌸"),
                    Map.entry(Set.of(EmotionType.HAPPY, EmotionType.SO_SO), "행복한 순간도 있었고, 평범한 시간도 있었네요. 오늘 하루도 잘 보내셨네요. 🌤️🙂"),
                    Map.entry(Set.of(EmotionType.FLUTTER, EmotionType.SO_SO), "설레는 순간도 있었고, 평범한 시간도 있었네요. 다양한 감정이 어우러진 하루였던 것 같네요. 🎈🍃")
            );
            return combinationMessages.getOrDefault(emotions, "긍정적인 감정들이 함께했네요. 멋진 하루예요! 🌟");
        } else {
            return "오늘 기분이 좋으셨군요!";
        }
    }
}
