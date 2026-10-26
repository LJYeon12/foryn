package com.forA.chatbot.chat.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.ChatHandler;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatOperationToken;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.dto.ChatRequest;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatOperationRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.chat.service.ChatOperationService;
import com.forA.chatbot.chat.service.ChatRecoveryService;
import com.forA.chatbot.chat.service.ChatService;
import com.forA.chatbot.chat.service.MongoChatWriter;
import com.forA.chatbot.chat.service.PreparedChatTurn;
import com.forA.chatbot.chat.service.UserProfileDelta;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.notification.scheduler.ChatNotificationScheduler;
import com.forA.chatbot.user.domain.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

class ChatSagaFaultTest {
    static AnnotationConfigApplicationContext context;
    static ChatService service;
    static ChatOperationRepository operations;
    static ChatRecoveryService recovery;
    static ChatOperationService coordinator;
    static ChatSessionRepository sessions;
    static ChatMessageRepository messages;
    static UserRepository users;
    static MongoTemplate mongo;
    static MongoChatWriter writer;
    static ChatFaultProbe probe;
    static JdbcTemplate sql;
    static ObjectMapper json;
    static final int REPETITIONS = Integer.parseInt(System.getenv().getOrDefault("CHAT_FAULT_REPETITIONS", "100"));

    @BeforeAll static void start() throws Exception {
        context = new AnnotationConfigApplicationContext(ChatFaultConfiguration.class);
        service = context.getBean(ChatService.class);
        operations = context.getBean(ChatOperationRepository.class);
        recovery = context.getBean(ChatRecoveryService.class);
        coordinator = context.getBean(ChatOperationService.class);
        sessions = context.getBean(ChatSessionRepository.class);
        messages = context.getBean(ChatMessageRepository.class);
        users = context.getBean(UserRepository.class);
        mongo = context.getBean(MongoTemplate.class);
        writer = context.getBean(MongoChatWriter.class);
        probe = context.getBean(ChatFaultProbe.class);
        sql = new JdbcTemplate(context.getBean(DataSource.class));
        json = context.getBean(ObjectMapper.class);
        Files.createDirectories(evidence());
    }

    @AfterAll static void close() { if (context != null) context.close(); }
    @AfterEach void resetFaults() {
        reset(mongo, writer, operations, context.getBean(ChatNotificationScheduler.class));
        probe.mode.set("NONE");
    }

    @Test void normalProfileTurn() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            assertThat(send(test).getCurrentStep()).isEqualTo("BIRTH_YEAR");
            observe("NORMAL_PROFILE", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
        }
    }

    @Test void mongoOnlyTurn() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("ACTION_FEEDBACK");
            send(test);
            assertThat(sql.queryForObject("SELECT sql_committed FROM chat_operations WHERE user_id=?", Boolean.class, test.userId()))
                .isFalse();
            observe("NORMAL_MONGO_ONLY", i, test, false, "CHAT_END", 2, "COMPLETED");
        }
    }

    @Test void realMysqlUpdateRejection() throws Exception {
        sql.execute("CREATE TRIGGER chat_fault_reject BEFORE UPDATE ON users FOR EACH ROW SIGNAL SQLSTATE '45000'");
        try {
            for (int i = 0; i < REPETITIONS; i++) {
                var test = fresh("GENDER");
                assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class);
                observe("MYSQL_UPDATE_REJECT", i, test, false, "GENDER", 0, "CANCELLED");
            }
        } finally { sql.execute("DROP TRIGGER chat_fault_reject"); }
    }

    @Test void definiteSqlRollbackAfterStage() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            probe.mode.set("ROLLBACK");
            assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class)
                .hasFieldOrPropertyWithValue("code", ErrorStatus.CHAT_PROCESSING_ERROR);
            observe("MYSQL_ROLLBACK_BEFORE_COMMIT", i, test, false, "GENDER", 0, "CANCELLED");
        }
    }

    @Test void successfulSqlCommitWithLostAcknowledgementIsNeverCompensated() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            probe.mode.set("ACK_LOST");
            send(test);
            observe("MYSQL_SUCCESS_ACK_LOST", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
        }
    }

    @Test void firstAndSecondMessageRejectionRollsBackEntireMongoTransaction() throws Exception {
        for (String step : new String[] {"GENDER", "ACTION_FEEDBACK"}) {
            for (int rejected : new int[] {1, 2}) {
                for (int i = 0; i < REPETITIONS; i++) {
                    var test = fresh(step);
                    var inserts = new AtomicInteger();
                    doAnswer(call -> {
                        if (inserts.incrementAndGet() == rejected) throw new IllegalStateException("Injected message rejection");
                        return call.callRealMethod();
                    }).when(mongo).insert(any(ChatMessage.class));
                    assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class);
                    reset(mongo);
                    observe("MESSAGE_" + rejected + "_" + step, i, test, false, step, 0, "CANCELLED");
                }
            }
        }
    }

    @Test void sessionWriteRejectionAlsoRollsBackBothMessages() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            doThrow(new IllegalStateException("Injected session rejection")).when(mongo)
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ChatSession.class));
            assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class);
            reset(mongo);
            observe("SESSION_WRITE_REJECT", i, test, false, "GENDER", 0, "CANCELLED");
        }
    }

    @Test void stageCommitAcknowledgementLossIsResolvedBeforeCancellation() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            stageAckLoss();
            assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class);
            reset(writer);
            observe("MONGO_STAGE_SUCCESS_ACK_LOST", i, test, false, "GENDER", 0, "CANCELLED");
        }
    }

    @Test void publishCommitAcknowledgementLossDoesNotDuplicateMessages() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            AtomicBoolean once = new AtomicBoolean();
            doAnswer(call -> {
                Object result = call.callRealMethod();
                if (!once.getAndSet(true)) throw new IllegalStateException("Lost publish acknowledgement");
                return result;
            }).when(writer).publish(any());
            send(test);
            reset(writer);
            observe("MONGO_PUBLISH_SUCCESS_ACK_LOST", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
        }
    }

    @Test void publishOutageKeepsPendingHiddenAndRecoversWithoutAi() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            doThrow(new IllegalStateException("Publish unavailable")).when(writer).publish(any());
            assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class)
                .hasFieldOrPropertyWithValue("code", ErrorStatus.CHAT_RECOVERY_PENDING);
            assertPending(test);
            reset(writer);
            recoverAfterRestore(test);
            observe("PUBLISH_OUTAGE_RECOVERED", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
        }
    }

    @Test void failedCompensationIsDurableAndRetryable() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            stageAckLoss();
            doThrow(new IllegalStateException("Compensation unavailable")).when(writer).cancel(any());
            assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class)
                .hasFieldOrPropertyWithValue("code", ErrorStatus.CHAT_RECOVERY_PENDING);
            assertPending(test);
            reset(writer);
            recoverAfterRestore(test);
            observe("COMPENSATION_OUTAGE_RECOVERED", i, test, false, "GENDER", 0, "CANCELLED");
        }
    }

    @Test void delayedOldWriterCannotCommitAfterRecoveryCancelledIt() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            ChatOperationToken old = coordinator.begin(test.userId(), test.sessionId(), "TURN");
            ChatOperationToken newer = operations.reclaim(old.id(), old.fence()).token();
            writer.fenceAndInspect(newer);
            writer.cancel(newer);
            operations.finish(newer, false);
            var session = sessions.findById(test.sessionId()).orElseThrow();
            var user = users.findById(test.userId()).orElseThrow();
            PreparedChatTurn late = new PreparedChatTurn(session, ChatMessage.builder().build(), ChatMessage.builder().build(),
                UserProfileDelta.between(user, user));
            assertThatThrownBy(() -> writer.stage(old, late)).isInstanceOf(ChatHandler.class);
            assertThatThrownBy(() -> writer.commitTurn(old, late)).isInstanceOf(ChatHandler.class);
            observe("OLD_WRITER_FENCED", i, test, false, "GENDER", 0, "CANCELLED");
        }
    }

    @Test void twoRecoveryWorkersConvergeToOneCommittedTurn() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < REPETITIONS; i++) {
                var test = fresh("GENDER");
                doThrow(new IllegalStateException("Publish unavailable")).when(writer).publish(any());
                assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class);
                reset(writer);
                expire(test);
                String id = latestOperation(test.userId());
                var first = workers.submit(() -> recovery.recover(id, null));
                var second = workers.submit(() -> recovery.recover(id, null));
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
                expire(test);
                recovery.recoverDue();
                observe("RECOVERY_WORKER_RACE", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
            }
        }
    }

    @Test void recoveryWaitsForAnInFlightSqlCommitInsteadOfAssumingRollback() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < REPETITIONS; i++) {
                var test = fresh("GENDER");
                probe.entered = new CountDownLatch(1);
                probe.release = new CountDownLatch(1);
                probe.mode.set("PAUSE");
                var request = workers.submit(() -> {
                    try { send(test); } catch (ChatHandler pending) { /* 다른 작업권 소유자가 이미 커밋된 같은 대화 차례를 완료할 수 있다. */ }
                });
                assertThat(probe.entered.await(10, TimeUnit.SECONDS)).isTrue();
                String id = latestOperation(test.userId());
                var resolving = workers.submit(() -> recovery.recover(id, operations.find(id).token().fence()));
                Thread.sleep(30);
                assertThat(resolving.isDone()).as("Recovery must wait for the original SQL guard lock").isFalse();
                probe.release.countDown();
                request.get(15, TimeUnit.SECONDS);
                resolving.get(15, TimeUnit.SECONDS);
                expire(test);
                recovery.recoverDue();
                observe("SQL_COMMIT_RECOVERY_RACE", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
            }
        }
    }

    @Test void independentJvmCrashesAndFreshProcessRecovery() throws Exception {
        int repetitions = Integer.parseInt(System.getenv().getOrDefault("CHAT_FAULT_PROCESS_REPETITIONS", "1"));
        for (int i = 0; i < repetitions; i++) {
            var cases = new ArrayList<Case>();
            String[] phases = {"PREPARING", "BEFORE_SQL", "AFTER_STAGE", "AFTER_SQL", "AFTER_PUBLISH", "DURING_CANCEL"};
            int[] exitCodes = {70, 71, 73, 72, 74, 75};
            for (int index = 0; index < phases.length; index++) {
                var test = fresh("GENDER");
                cases.add(test);
                assertThat(worker(phases[index], test)).isEqualTo(exitCodes[index]);
                expire(test);
            }
            long recoveryStarted = System.nanoTime();
            assertThat(worker("RECOVER", cases.getFirst())).isZero();
            double recoveryMillis = (System.nanoTime() - recoveryStarted) / 1_000_000.0;
            for (int index = 0; index < phases.length; index++) {
                cases.get(index).measurements().put("recoveryMillis", recoveryMillis);
                boolean committed = phases[index].equals("AFTER_SQL") || phases[index].equals("AFTER_PUBLISH");
                observe("JVM_CRASH_" + phases[index], i, cases.get(index), committed,
                    committed ? "BIRTH_YEAR" : "GENDER", committed ? 2 : 0, committed ? "COMPLETED" : "CANCELLED");
            }
            System.out.println("CHAT_FAULT JVM_CRASH checkpoints=6 iteration=" + i);
        }
    }

    @Test void completionRecordFailureDoesNotDuplicateAnAlreadyPublishedTurn() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            AtomicBoolean once = new AtomicBoolean();
            doAnswer(call -> {
                if (!once.getAndSet(true)) throw new IllegalStateException("Completion record unavailable");
                return call.callRealMethod();
            }).when(operations).finish(any(), eq(true));
            send(test);
            reset(operations);
            observe("COMPLETION_RECORD_RETRY", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
        }
    }

    @Test void initializationAndForceNewSessionAreMongoAtomic() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var fixture = fresh("GENDER");
            sessions.deleteById(fixture.sessionId());
            var first = service.initializeSession(fixture.userId());
            Case initial = new Case(fixture.userId(), first.getSessionId(), "GENDER");
            observe("INITIALIZE", i, initial, false, "GENDER", 1, "COMPLETED");
            var second = service.forceInitializeSession(fixture.userId());
            assertThat(sessions.findById(first.getSessionId()).orElseThrow().getEndedAt()).isNotNull();
            observe("FORCE_INITIALIZE", i, new Case(fixture.userId(), second.getSessionId(), "GENDER"),
                false, "GENDER", 1, "COMPLETED");
        }
    }

    @Test void failedForceNewSessionDoesNotCloseThePreviousSession() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            doThrow(new IllegalStateException("Initial bot insertion rejected")).when(mongo)
                .insert(any(ChatMessage.class), eq("chat_messages"));
            assertThatThrownBy(() -> service.forceInitializeSession(test.userId())).isInstanceOf(RuntimeException.class);
            reset(mongo);
            assertThat(sessions.findByUserId(test.userId())).hasSize(1);
            assertThat(sessions.findById(test.sessionId()).orElseThrow().getEndedAt()).isNull();
            observe("FORCE_INITIALIZE_ROLLBACK", i, test, false, "GENDER", 0, "CANCELLED");
        }
    }

    @Test void deletionCannotRacePendingWorkOrBeUndoneByAnOldWriter() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            ChatOperationToken old = coordinator.begin(test.userId(), test.sessionId(), "TURN");
            assertThatThrownBy(() -> service.deleteAllChatDataByUserId(test.userId())).isInstanceOf(ChatHandler.class)
                .hasFieldOrPropertyWithValue("code", ErrorStatus.CHAT_OPERATION_BUSY);
            coordinator.cancelPreparation(old);
            service.deleteAllChatDataByUserId(test.userId());
            assertThatThrownBy(() -> writer.lifecycle(old, () -> sessions.save(ChatSession.builder()
                .id(test.sessionId()).userId(test.userId()).currentStep("GENDER").build()))).isInstanceOf(ChatHandler.class);
            observe("DELETE_OLD_WRITER_FENCED", i, test, false, "DELETED", 0, "COMPLETED");
        }
    }

    @Test void allMessageReadersIncludeLegacyDataAndHidePending() {
        var test = fresh("GENDER");
        for (String status : new String[] {null, "COMMITTED", "PENDING"}) {
            mongo.insert(ChatMessage.builder().sessionId(test.sessionId()).senderType(ChatMessage.SenderType.USER)
                .chatStep("EMOTION_SELECT").messageType("TEXT").messageContent("synthetic").writeStatus(status).build());
        }
        assertThat(messages.findBySessionIdOrderBySentAtAsc(test.sessionId())).hasSize(2);
        assertThat(messages.countBySessionId(test.sessionId())).isEqualTo(2);
        assertThat(messages.findBySessionIdInOrderBySentAtAsc(java.util.List.of(test.sessionId()))).hasSize(2);
        assertThat(messages.findBySessionIdAndSenderTypeAndChatStep(test.sessionId(), ChatMessage.SenderType.USER, "EMOTION_SELECT"))
            .hasSize(2);
        assertThat(messages.findBySessionIdInAndSenderTypeAndChatStep(java.util.List.of(test.sessionId()),
            ChatMessage.SenderType.USER, "EMOTION_SELECT")).hasSize(2);
        assertThat(service.getRecentChatHistory(test.userId())).hasSize(2);
        // 테스트 준비용 PENDING 문서에는 작업 일지가 없다. 호환성 검증 후 삭제한다.
        mongo.remove(Query.query(Criteria.where("session_id").is(test.sessionId()).and("write_status").is("PENDING")), ChatMessage.class);
    }

    @Test void failedSkillSelectionAndInvalidConfirmationDoNotChangeNotifications() {
        var test = fresh("SKILL_SELECT");
        var session = sessions.findById(test.sessionId()).orElseThrow();
        session.setTemporaryData("selectedSkillName", "synthetic skill");
        sessions.save(session);
        AtomicInteger inserts = new AtomicInteger();
        doAnswer(call -> {
            if (inserts.incrementAndGet() == 2) throw new IllegalStateException("Second message rejected");
            return call.callRealMethod();
        }).when(mongo).insert(any(ChatMessage.class));
        assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class);
        var notifications = context.getBean(ChatNotificationScheduler.class);
        org.mockito.Mockito.verifyNoInteractions(notifications);
        reset(mongo);
        doAnswer(call -> {
            assertThat(messages.countBySessionId(test.sessionId())).isEqualTo(2);
            assertThat(operations.find(latestOperation(test.userId())).status()).isEqualTo("COMPLETED");
            return null;
        }).when(notifications).scheduleNotification(any(), any(), any());
        send(test);
        org.mockito.Mockito.verify(notifications).scheduleNotification(test.sessionId(), test.userId(), "synthetic skill");
        ChatRequest invalid = new ChatRequest();
        invalid.setResponseValue("invalid synthetic button");
        assertThatThrownBy(() -> service.handleUserResponse(test.userId(), test.sessionId(), invalid)).isInstanceOf(ChatHandler.class);
        org.mockito.Mockito.verify(notifications, org.mockito.Mockito.never()).cancelNotification(any());
    }

    @Test void osivManagedUserRemainsUnchangedAndSqlConnectionIsReleasedDuringPreparation() {
        var test = fresh("GENDER");
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        var entityManager = factory.createEntityManager();
        var manager = context.getBean("transactionManager", org.springframework.transaction.PlatformTransactionManager.class);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(factory,
            new org.springframework.orm.jpa.EntityManagerHolder(entityManager));
        var handler = context.getBean(com.forA.chatbot.chat.component.ChatStepHandlerProvider.class).getHandler("BIRTH_YEAR");
        try {
            User managed = transaction.execute(status -> users.findById(test.userId()).orElseThrow());
            doAnswer(call -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(managed.getGender()).isEqualTo(Gender.UNKNOWN);
                assertThat(context.getBean(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean().getActiveConnections()).isZero();
                return call.callRealMethod();
            }).when(handler).getBotMessage(any(), any());
            send(test);
            assertThat(users.findById(test.userId()).orElseThrow().getGender()).isEqualTo(Gender.MALE);
        } finally {
            reset(handler);
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(factory);
            entityManager.close();
        }
    }

    @Test void deactivationDuringPreparationDoesNotUpdateProfile() {
        var test = fresh("GENDER");
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        var entityManager = factory.createEntityManager();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
            context.getBean("transactionManager", org.springframework.transaction.PlatformTransactionManager.class));
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(factory,
            new org.springframework.orm.jpa.EntityManagerHolder(entityManager));
        var handler = context.getBean(com.forA.chatbot.chat.component.ChatStepHandlerProvider.class).getHandler("BIRTH_YEAR");
        try {
            transaction.execute(status -> users.findById(test.userId()).orElseThrow());
            doAnswer(call -> {
                sql.update("UPDATE users SET is_deleted=TRUE WHERE id=?", test.userId());
                return call.callRealMethod();
            }).when(handler).getBotMessage(any(), any());
            assertThatThrownBy(() -> send(test)).isInstanceOf(ChatHandler.class);
            entityManager.clear();
            assertThat(users.findById(test.userId()).orElseThrow().getGender()).isEqualTo(Gender.UNKNOWN);
            assertThat(messages.countBySessionId(test.sessionId())).isZero();
        } finally {
            reset(handler);
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(factory);
            entityManager.close();
        }
    }

    @Test void allFiveProfileFieldsUseTheSameRecoverableProtocol() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            send(test, "남성");
            send(test, "1995");
            send(test, com.forA.chatbot.user.domain.enums.JobType.values()[0].getName());
            var disorder = java.util.Arrays.stream(com.forA.chatbot.user.domain.enums.DisorderType.values())
                .filter(value -> value != com.forA.chatbot.user.domain.enums.DisorderType.NONE).findFirst().orElseThrow();
            send(test, disorder.getName());
            var symptom = com.forA.chatbot.user.domain.enums.SymptomType.getByDisorderTypes(java.util.Set.of(disorder))
                .iterator().next();
            send(test, symptom.getDescription());
            User user = users.findById(test.userId()).orElseThrow();
            assertThat(user.getBirthYear()).isEqualTo(1995);
            assertThat(user.getJobs()).containsExactly(com.forA.chatbot.user.domain.enums.JobType.values()[0]);
            assertThat(user.getDisorders()).containsExactly(disorder);
            assertThat(user.getSymptoms()).containsExactly(symptom);
            assertThat(sessions.findById(test.sessionId()).orElseThrow().getStateVersion()).isEqualTo(5);
            observe("FIVE_PROFILE_STEPS", i, test, true, "EMOTION_SELECT", 10, "COMPLETED");
        }
    }

    @Test void legacySessionWithoutVersionIsUpdatedInsteadOfInserted() throws Exception {
        for (int i = 0; i < REPETITIONS; i++) {
            var test = fresh("GENDER");
            mongo.updateFirst(Query.query(Criteria.where("_id").is(test.sessionId())),
                new org.springframework.data.mongodb.core.query.Update().unset("state_version"), ChatSession.class);
            send(test);
            assertThat(sessions.findByUserId(test.userId())).hasSize(1);
            observe("LEGACY_MISSING_VERSION", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
        }
    }

    @Test void accountTransactionKeepsTheGuardLockedUntilItsOwnCommit() throws Exception {
        var test = fresh("GENDER");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
            context.getBean("transactionManager", org.springframework.transaction.PlatformTransactionManager.class));
        try (var worker = Executors.newSingleThreadExecutor()) {
            var attempt = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
            transaction.executeWithoutResult(status -> {
                service.deleteAllChatDataByUserId(test.userId());
                attempt.set(worker.submit(() -> {
                    try {
                        var token = coordinator.begin(test.userId(), null, "INITIALIZE");
                        coordinator.cancelPreparation(token);
                    } catch (ChatHandler busy) {
                        assertThat(busy.getCode()).isEqualTo(ErrorStatus.CHAT_OPERATION_BUSY);
                    }
                }));
                try { Thread.sleep(40); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                assertThat(attempt.get().isDone()).as("Account transaction still owns the SQL guard lock").isFalse();
            });
            attempt.get().get(15, TimeUnit.SECONDS);
            assertThat(sessions.findById(test.sessionId())).isEmpty();
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM chat_operation_guards WHERE user_id=? AND operation_id IS NOT NULL",
                Integer.class, test.userId())).isZero();
        }
    }

    @Test void staleNicknameWriteCannotUndoCommittedChatProfile() throws Exception {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
            context.getBean("transactionManager", org.springframework.transaction.PlatformTransactionManager.class));
        try (var worker = Executors.newSingleThreadExecutor()) {
            for (int i = 0; i < REPETITIONS; i++) {
                var test = fresh("GENDER");
                var loaded = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                var nickname = worker.submit(() -> transaction.executeWithoutResult(status -> {
                    User stale = users.findById(test.userId()).orElseThrow();
                    stale.updateNickname("updated synthetic nickname");
                    loaded.countDown();
                    try { assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                    users.flush();
                }));
                try {
                    assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
                    send(test);
                } finally { release.countDown(); }
                nickname.get(15, TimeUnit.SECONDS);
                assertThat(users.findById(test.userId()).orElseThrow().getNickname()).isEqualTo("updated synthetic nickname");
                observe("NICKNAME_UPDATE_RACE", i, test, true, "BIRTH_YEAR", 2, "COMPLETED");
            }
        }
    }

    @Test void resetRefreshesProfileAfterAcquiringTheChatGuard() throws Exception {
        var account = context.getBean(com.forA.chatbot.user.service.UserService.class);
        Object target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(account);
        var resetUsers = (UserRepository) org.springframework.test.util.ReflectionTestUtils.getField(target, "userRepository");
        try (var worker = Executors.newSingleThreadExecutor()) {
            for (int i = 0; i < REPETITIONS; i++) {
                var test = fresh("GENDER");
                var loaded = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                doAnswer(call -> {
                    Object result = users.findById(test.userId());
                    loaded.countDown();
                    assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                    return result;
                }).when(resetUsers).findById(test.userId());
                var resetting = worker.submit(() -> account.resetUserData(test.userId()));
                try {
                    assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
                    send(test);
                } finally { release.countDown(); }
                resetting.get(15, TimeUnit.SECONDS);
                reset(resetUsers);
                observe("PROFILE_RESET_RACE", i, test, false, "DELETED", 0, "COMPLETED");
            }
        } finally { reset(resetUsers); }
    }

    private static int worker(String phase, Case test) throws Exception {
        String id = phase + "-" + UUID.randomUUID();
        Path arguments = evidence().resolve(id + ".args");
        String classpath = Files.readString(Path.of(System.getProperty("chat.fault.classpathFile"))).replace('\\', '/');
        Files.writeString(arguments, "-Xmx192m\n-cp\n\"" + classpath + "\"\n" + ChatFaultWorker.class.getName()
            + "\n" + phase + "\n" + test.userId() + "\n" + test.sessionId() + "\n");
        Path log = evidence().resolve(id + ".log");
        String binary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(binary, "@" + arguments.toAbsolutePath())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("Child process timeout: " + log);
        }
        assertThat(process.exitValue()).withFailMessage("Worker %s failed; inspect %s", phase, log)
            .isIn(0, 70, 71, 72, 73, 74, 75);
        return process.exitValue();
    }

    private static void stageAckLoss() {
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("Lost stage acknowledgement AFTER real Mongo commit");
        }).when(writer).stage(any(), any());
    }

    private static void assertPending(Case test) {
        assertThat(messages.findBySessionIdOrderBySentAtAsc(test.sessionId())).isEmpty();
        assertThat(service.getRecentChatHistory(test.userId())).isEmpty();
        assertThat(mongo.count(Query.query(Criteria.where("session_id").is(test.sessionId())
            .and("write_status").is("PENDING")), ChatMessage.class)).isEqualTo(2);
        assertThat(sessions.findById(test.sessionId()).orElseThrow().getCurrentStep()).isEqualTo("GENDER");
    }

    static Case fresh(String step) {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid")
            .providerType(ProviderType.APPLE).providerUserId(UUID.randomUUID().toString()).fullName("synthetic")
            .nickname("synthetic").build());
        ChatSession session = ChatSession.builder().userId(user.getId()).currentStep(step)
            .onboardingCompleted(!step.equals("GENDER")).startedAt(LocalDateTime.now()).build();
        session.setTemporaryData("selectedEmotions", "SADNESS");
        sessions.save(session);
        return new Case(user.getId(), session.getId(), step);
    }

    static com.forA.chatbot.chat.dto.ChatResponse send(Case test) {
        return send(test, test.step().equals("GENDER") ? "남성" : "조금 나아졌어요");
    }

    static com.forA.chatbot.chat.dto.ChatResponse send(Case test, String value) {
        ChatRequest request = new ChatRequest();
        request.setResponseValue(value);
        long started = System.nanoTime();
        test.measurements().merge("serviceCalls", 1, (before, increment) -> ((Number) before).intValue() + 1);
        try {
            return service.handleUserResponse(test.userId(), test.sessionId(), request);
        } catch (RuntimeException failure) {
            test.measurements().merge("serviceExceptions", 1, (before, increment) -> ((Number) before).intValue() + 1);
            throw failure;
        } finally {
            test.measurements().put("lastServiceMillis", (System.nanoTime() - started) / 1_000_000.0);
        }
    }

    static void recoverAfterRestore(Case test) {
        long started = System.nanoTime();
        expire(test);
        recovery.recoverDue();
        test.measurements().put("recoveryMillis", (System.nanoTime() - started) / 1_000_000.0);
    }

    static void expire(Case test) {
        sql.update("UPDATE chat_operations SET lease_until=0,next_attempt_at=0 WHERE user_id=?", test.userId());
        test.measurements().put("leaseAccelerated", true);
    }

    static String latestOperation(Long userId) {
        return sql.queryForObject("SELECT operation_id FROM chat_operations WHERE user_id=? ORDER BY created_at DESC LIMIT 1",
            String.class, userId);
    }

    static Path evidence() { return Path.of(System.getProperty("chat.fault.evidence")); }

    static void observe(String condition, int iteration, Case test, boolean changed, String step, int count, String status)
            throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("condition", condition);
        row.put("iteration", iteration);
        row.put("expectedMessages", count);
        row.put("visibleMessages", messages.findBySessionIdOrderBySentAtAsc(test.sessionId()).size());
        row.put("rawMessages", mongo.count(Query.query(Criteria.where("session_id").is(test.sessionId())), ChatMessage.class));
        row.put("pendingMessages", mongo.count(Query.query(Criteria.where("session_id").is(test.sessionId())
            .and("write_status").is("PENDING")), ChatMessage.class));
        ChatSession current = sessions.findById(test.sessionId()).orElse(null);
        row.put("pendingTurns", current == null || current.getPendingTurn() == null ? 0 : 1);
        row.put("expectedProfileChanged", changed);
        row.put("profileChanged", users.findById(test.userId()).orElseThrow().getGender() == Gender.MALE);
        row.put("expectedStep", step);
        row.put("currentStep", current == null ? "DELETED" : current.getCurrentStep());
        row.put("guardActive", sql.queryForObject("SELECT COUNT(*) FROM chat_operation_guards WHERE user_id=? AND operation_id IS NOT NULL",
            Integer.class, test.userId()) != 0);
        row.put("expectedJournalStatus", status);
        row.put("journalStatus", operations.find(latestOperation(test.userId())).status());
        row.put("serviceCalls", test.measurements().getOrDefault("serviceCalls", 0));
        row.put("serviceExceptions", test.measurements().getOrDefault("serviceExceptions", 0));
        row.put("lastServiceMillis", test.measurements().get("lastServiceMillis"));
        row.put("recoveryMillis", test.measurements().get("recoveryMillis"));
        row.put("leaseAccelerated", test.measurements().getOrDefault("leaseAccelerated", false));
        var groups = mongo.find(Query.query(Criteria.where("session_id").is(test.sessionId())), ChatMessage.class).stream()
            .collect(java.util.stream.Collectors.groupingBy(message -> message.getOperationId() + ":" + message.getSenderType(),
                java.util.stream.Collectors.counting()));
        row.put("duplicateMessages", groups.values().stream().mapToLong(value -> Math.max(0, value - 1)).sum());
        Files.writeString(evidence().resolve("observations.ndjson"), json.writeValueAsString(row) + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        assertThat(row.get("visibleMessages")).isEqualTo(count);
        assertThat(((Number) row.get("rawMessages")).intValue()).isEqualTo(count);
        assertThat(((Number) row.get("pendingMessages")).intValue()).isZero();
        assertThat(row.get("pendingTurns")).isEqualTo(0);
        assertThat(row.get("profileChanged")).isEqualTo(changed);
        assertThat(row.get("currentStep")).isEqualTo(step);
        assertThat(row.get("guardActive")).isEqualTo(false);
        assertThat(row.get("journalStatus")).isEqualTo(status);
        if (iteration == REPETITIONS - 1) System.out.println("CHAT_FAULT " + condition + " checked=" + REPETITIONS);
    }

    record Case(Long userId, String sessionId, String step, Map<String, Object> measurements) {
        Case(Long userId, String sessionId, String step) {
            this(userId, sessionId, step, new java.util.concurrent.ConcurrentHashMap<>());
        }
    }
}
