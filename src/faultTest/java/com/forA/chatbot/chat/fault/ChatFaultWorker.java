package com.forA.chatbot.chat.fault;

import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.service.ChatOperationService;
import com.forA.chatbot.chat.service.ChatRecoveryService;
import com.forA.chatbot.chat.service.ChatService;
import com.forA.chatbot.chat.service.MongoChatWriter;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** JVM 강제 종료로 close(), Spring 콜백, Java finally 블록을 의도적으로 건너뛴다. */
public class ChatFaultWorker {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(ChatFaultConfiguration.class)) {
            String phase = args[0];
            if (phase.equals("RECOVER")) {
                context.getBean(ChatRecoveryService.class).recoverDue();
                return;
            }
            Long userId = Long.valueOf(args[1]);
            String sessionId = args[2];
            if (phase.equals("PREPARING")) {
                context.getBean(ChatOperationService.class).begin(userId, sessionId, "TURN");
                Runtime.getRuntime().halt(70);
            }
            var writer = context.getBean(MongoChatWriter.class);
            if (phase.equals("AFTER_STAGE")) {
                Mockito.doAnswer(call -> {
                    call.callRealMethod();
                    Runtime.getRuntime().halt(73);
                    return null;
                }).when(writer).stage(Mockito.any(), Mockito.any());
            } else if (phase.equals("AFTER_PUBLISH")) {
                Mockito.doAnswer(call -> {
                    call.callRealMethod();
                    Runtime.getRuntime().halt(74);
                    return null;
                }).when(writer).publish(Mockito.any());
            } else if (phase.equals("DURING_CANCEL")) {
                context.getBean(ChatFaultProbe.class).mode.set("ROLLBACK");
                Mockito.doAnswer(call -> {
                    call.callRealMethod();
                    Runtime.getRuntime().halt(75);
                    return null;
                }).when(writer).cancel(Mockito.any());
            } else if (phase.equals("BEFORE_SQL")) {
                context.getBean(ChatFaultProbe.class).mode.set("HALT_BEFORE_SQL");
            } else if (phase.equals("AFTER_SQL")) {
                context.getBean(ChatFaultProbe.class).mode.set("HALT_AFTER_SQL");
            }
            ChatRequest request = new ChatRequest();
            request.setResponseValue("남성");
            context.getBean(ChatService.class).handleUserResponse(userId, sessionId, request);
            throw new IllegalStateException("Worker missed the requested crash checkpoint");
        }
    }
}
