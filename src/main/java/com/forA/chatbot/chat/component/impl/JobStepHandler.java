package com.forA.chatbot.chat.component.impl;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.component.ChatStepHandler;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.domain.enums.MessageType;
import com.forA.chatbot.chat.dto.ButtonOption;
import com.forA.chatbot.chat.dto.ChatBotMessage;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.JobType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobStepHandler implements ChatStepHandler {
    @Override
    public void validateState(ChatSession session, User user) {
        if (user.getBirthYear() == null) {
            log.warn("데이터 불일치 감지(Atomic Gap). 세션을 초기 단계로 복구합니다. UserId: {}", user.getId());
            session.setCurrentStep(ChatStep.BIRTH_YEAR.name());
            throw new ChatHandler(ErrorStatus.INCONSISTENT_CHAT_STATE);
        }
    }
    @Override
    public String getStepName() {
        return ChatStep.JOB_TYPE.name();
    }

    @Override
    public String handleRequest(ChatSession session, User user, ChatRequest request) {
        String responseValue = request.getResponseValue();

        String[] selections = responseValue.split(",");
        if (selections.length > 2) {
            throw new ChatHandler(ErrorStatus.CHAT_SELECTION_LIMIT_EXCEEDED);
        }
        Set<JobType> selectedJobs = new HashSet<>();
        for (String selection : selections) {
            String label = selection.trim();
            JobType job = Arrays.stream(JobType.values())
                    .filter(j -> j.getName().equals(label))
                    .findFirst()
                    .orElseThrow(() -> new ChatHandler(ErrorStatus.INVALID_BUTTON_SELECTION));
            selectedJobs.add(job);
        }
        user.updateJobs(selectedJobs);
        return ChatStep.DISORDER_TYPE.name();
    }

    @Override
    public ChatBotMessage getBotMessage(ChatSession session, User user) {

        List<ButtonOption> options = Arrays.stream(JobType.values())
                .map(job -> ButtonOption.builder()
                        .label(job.getName())
                        .multiSelect(true) // 다중 선택 가능
                        .build())
                .collect(Collectors.toList());

        return ChatBotMessage.builder()
                .content("지금 하는 일이 어떻게 되는지 궁금해요! 최대 2개까지 선택할 수 있어요!")
                .type(MessageType.OPTION)
                .options(options)
                .build();
    }
}
