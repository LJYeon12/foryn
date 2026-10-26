package com.forA.chatbot.chat.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class ChatProfileWriter {
    @PersistenceContext private EntityManager entityManager;

    public void apply(Long userId, UserProfileDelta delta) {
        User latest = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (latest == null) throw new UserHandler(ErrorStatus.USER_NOT_FOUND);
        // OSIV에 준비 단계 조회에서 읽은 오래된 관리 객체가 남아 있을 수 있다.
        entityManager.refresh(latest, LockModeType.PESSIMISTIC_WRITE);
        if (latest.isDeleted()) throw new UserHandler(ErrorStatus.USER_NOT_FOUND);
        delta.applyTo(latest);
        entityManager.flush();
    }
}
