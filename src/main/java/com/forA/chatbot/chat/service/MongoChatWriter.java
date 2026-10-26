package com.forA.chatbot.chat.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.ChatSessionState;
import com.forA.chatbot.chat.domain.PendingChatTurn;
import com.mongodb.MongoException;
import java.util.List;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 모든 채팅 변경은 같은 Mongo 트랜잭션에서 사용자별 동일한 작업권 보호 문서에 기록한다. */
@Component
public class MongoChatWriter {
    public static final String GUARDS = "chat_write_guards";
    private final MongoTemplate mongo;
    private final TransactionTemplate transaction;

    public MongoChatWriter(MongoTemplate mongo,
            @Qualifier("mongoTransactionManager") PlatformTransactionManager manager) {
        this.mongo = mongo;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setTimeout(20);
    }

    public void reserve(ChatOperationToken token) {
        run(() -> {
            Document guard = head(token.userId());
            if (token.id().equals(guard.getString("operation_id")) && fence(guard) == token.fence()) return null;
            if (fence(guard) >= token.fence()) throw conflict();
            setHead(token, guard, "RESERVED");
            return null;
        });
    }

    public void commitTurn(ChatOperationToken token, PreparedChatTurn turn) {
        run(() -> {
            if (!transition(token, "RESERVED", "COMMITTED")) return null;
            insertMessages(token, turn, "COMMITTED");
            applyState(turn.session().getId(), token, turn.session().getStateVersion(), ChatSessionState.from(turn.session()));
            return null;
        });
    }

    public void stage(ChatOperationToken token, PreparedChatTurn turn) {
        run(() -> {
            if (!transition(token, "RESERVED", "STAGED")) return null;
            insertMessages(token, turn, "PENDING");
            Query query = sessionVersion(turn.session().getId(), token.userId(), turn.session().getStateVersion());
            query.addCriteria(Criteria.where("pending_turn").is(null));
            if (mongo.updateFirst(query, new Update().set("pending_turn", new PendingChatTurn(token.id(),
                    turn.session().getStateVersion(), ChatSessionState.from(turn.session()))), ChatSession.class)
                    .getMatchedCount() != 1) throw conflict();
            return null;
        });
    }

    public void publish(ChatOperationToken token) {
        run(() -> {
            if (!transition(token, "STAGED", "COMMITTED")) return null;
            ChatSession session = mongo.findOne(Query.query(Criteria.where("_id").is(token.sessionId())
                .and("user_id").is(token.userId()).and("pending_turn.operationId").is(token.id())), ChatSession.class);
            if (session == null || session.getPendingTurn() == null) throw new IllegalStateException("Missing staged turn");
            PendingChatTurn pending = session.getPendingTurn();
            long changed = mongo.updateMulti(Query.query(Criteria.where("operation_id").is(token.id())
                    .and("session_id").is(token.sessionId()).and("write_status").is("PENDING")),
                new Update().set("write_status", "COMMITTED"), ChatMessage.class).getModifiedCount();
            if (changed != 2) throw new IllegalStateException("Incomplete staged turn");
            applyState(token.sessionId(), token, pending.expectedVersion(), pending.state());
            return null;
        });
    }

    /** 이미 실행 중인 오래된 트랜잭션도 차단한다. 문서가 없다는 사실만으로 실패를 판단하지 않는다. */
    public String fenceAndInspect(ChatOperationToken token) {
        return run(() -> {
            Document guard = head(token.userId());
            if (fence(guard) > token.fence()) throw conflict();
            String outcome = token.id().equals(guard.getString("operation_id")) ? guard.getString("outcome") : "RESERVED";
            setHead(token, guard, outcome);
            return outcome;
        });
    }

    public void cancel(ChatOperationToken token) {
        run(() -> {
            Document guard = requireHead(token);
            String outcome = guard.getString("outcome");
            if ("CANCELLED".equals(outcome)) return null;
            if (!List.of("RESERVED", "STAGED").contains(outcome)) throw conflict();
            setHead(token, guard, "CANCELLED");
            mongo.remove(Query.query(Criteria.where("operation_id").is(token.id()).and("write_status").is("PENDING")),
                ChatMessage.class);
            mongo.updateMulti(Query.query(Criteria.where("user_id").is(token.userId())
                .and("pending_turn.operationId").is(token.id())), new Update().unset("pending_turn"), ChatSession.class);
            return null;
        });
    }

    public <T> T lifecycle(ChatOperationToken token, Supplier<T> action) {
        return run(() -> {
            if (!transition(token, "RESERVED", "COMMITTED")) throw conflict();
            return action.get();
        });
    }

    public void repair(ChatOperationToken token, ChatSession session) {
        lifecycle(token, () -> {
            applyState(session.getId(), token, session.getStateVersion(), ChatSessionState.from(session));
            return null;
        });
    }

    private void insertMessages(ChatOperationToken token, PreparedChatTurn turn, String status) {
        ChatMessage user = turn.userMessage();
        ChatMessage bot = turn.botMessage();
        user.setId(token.id() + ":user");
        bot.setId(token.id() + ":bot");
        for (ChatMessage message : List.of(user, bot)) {
            message.setOperationId(token.id());
            message.setWriteStatus(status);
            mongo.insert(message);
        }
    }

    private void applyState(String sessionId, ChatOperationToken token, long version, ChatSessionState state) {
        Update update = new Update().set("current_step", state.currentStep())
            .set("onboarding_completed", state.onboardingCompleted()).set("temporary_data", state.temporaryData())
            .set("ended_at", state.endedAt()).set("last_interaction_at", state.lastInteractionAt())
            .set("state_version", version + 1).set("last_committed_operation_id", token.id()).unset("pending_turn");
        if (mongo.updateFirst(sessionVersion(sessionId, token.userId(), version), update, ChatSession.class)
                .getMatchedCount() != 1) throw conflict();
    }

    private Query sessionVersion(String id, Long userId, long version) {
        Query query = Query.query(Criteria.where("_id").is(id).and("user_id").is(userId));
        query.addCriteria(version == 0 ? new Criteria().orOperator(Criteria.where("state_version").is(0),
            Criteria.where("state_version").exists(false)) : Criteria.where("state_version").is(version));
        return query;
    }

    private Document head(Long userId) {
        Query query = Query.query(Criteria.where("_id").is(userId));
        mongo.upsert(query, new Update().setOnInsert("fence", 0L).setOnInsert("outcome", "IDLE"), GUARDS);
        return mongo.findOne(query, Document.class, GUARDS);
    }

    private Document requireHead(ChatOperationToken token) {
        Document guard = head(token.userId());
        if (!token.id().equals(guard.getString("operation_id")) || fence(guard) != token.fence()) throw conflict();
        return guard;
    }

    private boolean transition(ChatOperationToken token, String from, String to) {
        Document guard = requireHead(token);
        if (to.equals(guard.getString("outcome"))) return false;
        if (!from.equals(guard.getString("outcome"))) throw conflict();
        setHead(token, guard, to);
        return true;
    }

    private void setHead(ChatOperationToken token, Document previous, String outcome) {
        Query query = Query.query(Criteria.where("_id").is(token.userId()).and("fence").is(fence(previous)));
        long matched = mongo.updateFirst(query, new Update().set("fence", token.fence())
            .set("operation_id", token.id()).set("outcome", outcome).inc("touch", 1), GUARDS).getMatchedCount();
        if (matched != 1) throw conflict();
    }

    private long fence(Document guard) { return ((Number) guard.get("fence")).longValue(); }
    private ChatHandler conflict() { return new ChatHandler(ErrorStatus.CHAT_STATE_CONFLICT); }

    private <T> T run(Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return transaction.execute(status -> action.get());
            } catch (RuntimeException failure) {
                boolean retry = false;
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    if (cause instanceof MongoException mongoFailure) {
                        if (mongoFailure.hasErrorLabel("UnknownTransactionCommitResult")) throw failure;
                        retry |= mongoFailure.hasErrorLabel("TransientTransactionError");
                    }
                }
                if (!retry || attempt >= 2) throw failure;
            }
        }
    }
}
