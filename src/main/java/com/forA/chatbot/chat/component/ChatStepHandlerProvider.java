package com.forA.chatbot.chat.component;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChatStepHandlerProvider {

    private final Map<String, ChatStepHandler> handlerMap;

    public ChatStepHandlerProvider(List<ChatStepHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        ChatStepHandler::getStepName,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.error("중복된 Step Handler가 감지되었습니다: {}", existing.getStepName());
                            throw new ChatHandler(ErrorStatus.DUPLICATE_CHAT_STEP_HANDLER);
                        }
                ));
        log.info("ChatStepHandlerProvider 초기화 완료. 로드된 핸들러 수: {}, 목록: {}", handlerMap.size(), handlerMap.keySet());
    }

    public ChatStepHandler getHandler(String stepName) {
        return Optional.ofNullable(handlerMap.get(stepName))
                .orElseThrow(() -> {
                    log.error("해당 Step의 Handler를 찾을 수 없습니다 : {}", stepName);
                    return new ChatHandler(ErrorStatus.CHAT_STEP_NOT_FOUND);
                });
    }
}
