package com.forA.chatbot.notification.fault;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

/** 명시적으로 실행하는 로컬 실험이다. 수신기는 GCP 대신 통제된 HTTP 테스트 대역을 사용한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class OutboxProcessFaultTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final List<Map<String, Object>> observations = new ArrayList<>();
  private String jdbcUrl;
  private Path evidence;
  private Receiver receiver;
  private Worker first;
  private Worker second;

  @BeforeAll
  void startLocalEnvironment() throws Exception {
    jdbcUrl = LocalTaskTransport.requireDisposableDatabase(System.getenv("OUTBOX_FAULT_JDBC_URL"));
    // 오늘의 미래 예약을 사용한다. 만료된 날짜를 잘못 검사하지 않도록 명확히 실패시킨다.
    assertThat(LocalTime.now(ZoneId.of("Asia/Seoul"))).isBefore(LocalTime.of(23, 50));
    evidence = Path.of(System.getProperty("outbox.fault.evidence"), Instant.now().toEpochMilli() + "-" + UUID.randomUUID());
    Files.createDirectories(evidence);
    Flyway flyway = Flyway.configure().dataSource(jdbcUrl, "root", "")
        .locations("filesystem:src/main/resources/db/migration").load();
    flyway.migrate();
    flyway.validate();
    receiver = new Receiver();
    first = new Worker();
    second = new Worker();
    observe("environment", Map.of("database", "MySQL", "workers", List.of(first.pid(), second.pid()),
        "externalBoundary", "loopback HTTP test double", "gcpConnected", false));
  }

  @AfterEach
  void saveEvidence() throws Exception {
    writeEvidence();
    if (receiver != null) {
      receiver.mode = "OK";
      receiver.release.countDown();
    }
  }

  private void writeEvidence() throws Exception {
    if (evidence != null) {
      JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("observations.json").toFile(), observations);
      if (receiver != null) {
        JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("receiver-state.json").toFile(),
            Map.of("accepted", receiver.accepted, "requestsPerTask", receiver.requests,
                "duplicateResponses", receiver.duplicates.get(), "pushCalls", receiver.pushes.get()));
      }
    }
  }

  @AfterAll
  void stopLocalProcesses() throws Exception {
    if (first != null) { first.close(); }
    if (second != null) { second.close(); }
    if (receiver != null) { receiver.close(); }
    saveEvidence();
  }

  @Test @Order(1)
  @DisplayName("MySQL: 계획과 Outbox가 실제 서비스 트랜잭션에서 함께 롤백된다")
  void realMysqlRollback() throws Exception {
    long plans = count("SELECT COUNT(*) FROM medication_bundles");
    long jobs = count("SELECT COUNT(*) FROM medication_schedule_outbox");
    assertThat(first.command(Map.of("operation", "rollback")).has("error")).isTrue();
    assertThat(count("SELECT COUNT(*) FROM medication_bundles")).isEqualTo(plans);
    assertThat(count("SELECT COUNT(*) FROM medication_schedule_outbox")).isEqualTo(jobs);
    observe("rollback_pass", Map.of("plansBeforeAndAfter", plans, "jobsBeforeAndAfter", jobs));
  }

  @Test @Order(2)
  @DisplayName("커밋 직후 JVM 강제 종료·재시작 후 메모리 이벤트 없이 복구한다")
  void killAfterCommitThenRestart() throws Exception {
    JsonNode seed = seed(first);
    long job = seed.path("jobId").asLong();
    String task = seed.path("taskId").asText();
    long killedPid = first.pid();
    observe("after_commit_before_registration_kill", snapshot(job));
    first.kill();
    assertThat(snapshot(job).get("status")).isEqualTo("PENDING");
    assertThat(receiver.calls(task)).isZero();
    first = new Worker();
    first.ok(Map.of("operation", "recover"));
    assertCompleted(job, 1);
    assertThat(receiver.calls(task)).isEqualTo(1);
    observe("commit_crash_recovery_pass", Map.of("killedPid", killedPid, "replacementPid", first.pid(),
        "taskId", task, "job", snapshot(job)));
  }

  @Test @Order(3)
  @DisplayName("HTTP 503 뒤 실제 1분 backoff 동안 호출하지 않고 복구한다")
  void registrationFailureWithRealBackoff() throws Exception {
    JsonNode seed = seed(first);
    long job = seed.path("jobId").asLong();
    String task = seed.path("taskId").asText();
    receiver.mode = "FAIL";
    first.ok(Map.of("operation", "dispatch", "jobId", job));
    Map<String, String> waiting = snapshot(job);
    assertThat(waiting.get("status")).isEqualTo("RETRY_WAITING");
    assertThat(waiting.get("attempts")).isEqualTo("1");
    first.ok(Map.of("operation", "dispatch", "jobId", job));
    assertThat(receiver.calls(task)).isEqualTo(1);
    observe("503_retry_waiting", waiting);
    receiver.mode = "OK";
    awaitDatabaseTime(job, "next_attempt_at", Duration.ofSeconds(75));
    first.ok(Map.of("operation", "dispatch", "jobId", job));
    assertCompleted(job, 2);
    assertThat(receiver.calls(task)).isEqualTo(2);
    assertThat(receiver.accepted.containsKey(task)).isTrue();
    observe("503_recovery_pass", Map.of("job", snapshot(job), "actualBackoffWait", true));
  }

  @Test @Order(4)
  @DisplayName("등록 후 TCP 응답 유실 시 같은 ID로 재시도하고 외부 등록은 하나다")
  void droppedAcknowledgement() throws Exception {
    JsonNode seed = seed(first);
    long job = seed.path("jobId").asLong();
    String task = seed.path("taskId").asText();
    receiver.mode = "DROP_AFTER_ACCEPT";
    first.ok(Map.of("operation", "dispatch", "jobId", job));
    assertThat(receiver.accepted.containsKey(task)).isTrue();
    assertThat(snapshot(job).get("status")).isEqualTo("RETRY_WAITING");
    observe("ack_lost_after_accept", snapshot(job));
    // 이 시나리오는 식별과 상태 조정을 검증한다. 타이머 정확도는 앞선 테스트에서 검증한다.
    makeDue(job);
    receiver.mode = "OK";
    first.ok(Map.of("operation", "dispatch", "jobId", job));
    assertCompleted(job, 2);
    assertThat(receiver.calls(task)).isEqualTo(2);
    assertThat(receiver.duplicates.get()).isGreaterThanOrEqualTo(1);
    observe("lost_ack_recovery_pass", Map.of("taskId", task, "acceptedEntriesForTask", 1,
        "httpAttempts", receiver.calls(task), "timerAcceleratedBySql", true, "job", snapshot(job)));
  }

  @Test @Order(5)
  @DisplayName("두 독립 JVM이 같은 MySQL 작업을 경쟁해도 활성 처리자는 하나다")
  void twoProcessClaimRace() throws Exception {
    List<Long> jobs = new ArrayList<>();
    for (int round = 0; round < 10; round++) {
      JsonNode seed = seed(first);
      long job = seed.path("jobId").asLong();
      String task = seed.path("taskId").asText();
      receiver.hold();
      first.begin(Map.of("operation", "dispatch", "jobId", job));
      second.begin(Map.of("operation", "dispatch", "jobId", job));
      assertThat(receiver.entered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(snapshot(job).get("status")).isEqualTo("PROCESSING");
      assertThat(snapshot(job).get("attempts")).isEqualTo("1");
      // 승자 JVM이 외부 요청을 유지하는 동안 패자 JVM이 반환해야 한다.
      JsonNode firstReply = null;
      JsonNode secondReply = null;
      Instant loserDeadline = Instant.now().plusSeconds(5);
      while (firstReply == null && secondReply == null && Instant.now().isBefore(loserDeadline)) {
        firstReply = first.replies.poll();
        secondReply = second.replies.poll();
        if (firstReply == null && secondReply == null) { Thread.sleep(10); }
      }
      assertThat((firstReply != null) ^ (secondReply != null)).as("Exactly one loser returns before release").isTrue();
      assertThat((firstReply != null ? firstReply : secondReply).path("ok").asBoolean()).isTrue();
      receiver.release.countDown();
      if (firstReply == null) { first.awaitOk(); } else { second.awaitOk(); }
      assertCompleted(job, 1);
      assertThat(receiver.calls(task)).isEqualTo(1);
      receiver.mode = "OK";
      jobs.add(job);
    }
    observe("two_process_claim_pass", Map.of("rounds", 10, "jobs", jobs, "callsPerTask", 1));
  }

  @Test @Order(6)
  @DisplayName("설정 변경·삭제 뒤 이전 요청을 실행하면 푸시 대역을 호출하지 않는다")
  void changedAndDeletedPlansDoNotPush() throws Exception {
    int pushes = receiver.pushes.get();
    JsonNode seed = seed(first);
    long bundle = seed.path("bundleId").asLong();
    first.ok(Map.of("operation", "dispatch", "jobId", seed.path("jobId").asLong()));
    first.ok(Map.of("operation", "update", "bundleId", bundle));
    second.ok(Map.of("operation", "deliver", "bundleId", bundle, "version", 0));
    assertThat(receiver.pushes.get()).isEqualTo(pushes);
    // 정상 대조군: 현재 버전은 한 번 발송하며 실제 MySQL 발송 로그가 중복 전달을 막는다.
    second.ok(Map.of("operation", "deliver", "bundleId", bundle, "version", 1));
    second.ok(Map.of("operation", "deliver", "bundleId", bundle, "version", 1));
    assertThat(receiver.pushes.get()).isEqualTo(pushes + 1);
    JsonNode removed = seed(first);
    long removedBundle = removed.path("bundleId").asLong();
    first.ok(Map.of("operation", "delete", "bundleId", removedBundle));
    second.ok(Map.of("operation", "deliver", "bundleId", removedBundle, "version", 0));
    assertThat(receiver.pushes.get()).isEqualTo(pushes + 1);
    observe("stale_delete_and_dedup_pass", Map.of("staleAndDeletedPushes", 0,
        "currentVersionRepeatedTwicePushes", 1, "bundleId", bundle));
  }

  @Test @Order(7)
  @DisplayName("4회 등록 실패는 FAILED로 보존하고 명시적 재시도로 복구한다")
  void exhaustedRetryAndManualRecovery() throws Exception {
    JsonNode seed = seed(first);
    long job = seed.path("jobId").asLong();
    String task = seed.path("taskId").asText();
    receiver.mode = "FAIL";
    for (int attempt = 1; attempt <= 4; attempt++) {
      first.ok(Map.of("operation", "dispatch", "jobId", job));
      assertThat(snapshot(job).get("attempts")).isEqualTo(Integer.toString(attempt));
      if (attempt < 4) { makeDue(job); }
    }
    assertThat(snapshot(job).get("status")).isEqualTo("FAILED");
    first.ok(Map.of("operation", "dispatch", "jobId", job));
    assertThat(receiver.calls(task)).isEqualTo(4);
    observe("exhausted_preserved", snapshot(job));
    receiver.mode = "OK";
    first.ok(Map.of("operation", "retry", "jobId", job));
    assertCompleted(job, 1);
    assertThat(receiver.calls(task)).isEqualTo(5);
    observe("manual_recovery_pass", Map.of("job", snapshot(job), "timerAcceleratedBySql", true));
  }

  @Test @Order(8)
  @DisplayName("선점 중 JVM 강제 종료 후 실제 5분 처리권 만료를 기다려 회수한다")
  void killDuringClaimAndWaitForRealLease() throws Exception {
    JsonNode seed = seed(first);
    long job = seed.path("jobId").asLong();
    String task = seed.path("taskId").asText();
    receiver.hold();
    first.begin(Map.of("operation", "dispatch", "jobId", job));
    assertThat(receiver.entered.await(10, TimeUnit.SECONDS)).isTrue();
    Map<String, String> claimed = snapshot(job);
    assertThat(claimed.get("status")).isEqualTo("PROCESSING");
    long killedPid = first.pid();
    first.kill();
    receiver.mode = "OK";
    receiver.release.countDown();
    assertThat(receiver.accepted.containsKey(task)).isFalse();
    second.ok(Map.of("operation", "recover"));
    assertThat(snapshot(job).get("status")).isEqualTo("PROCESSING");
    observe("worker_killed_lease_active", Map.of("killedPid", killedPid, "job", snapshot(job)));
    awaitDatabaseTime(job, "lock_until", Duration.ofMinutes(6));
    second.ok(Map.of("operation", "recover"));
    assertCompleted(job, 2);
    assertThat(receiver.calls(task)).isEqualTo(2);
    observe("real_lease_crash_recovery_pass", Map.of("job", snapshot(job),
        "killedPid", killedPid, "successorPid", second.pid(), "leaseTimestampEdited", false));
  }

  private JsonNode seed(Worker worker) throws Exception {
    JsonNode seed = worker.command(Map.of("operation", "seed"));
    assertThat(seed.has("error")).isFalse();
    assertThat(seed.path("status").asText()).isEqualTo("PENDING");
    return seed;
  }

  private void assertCompleted(long job, int attempts) throws Exception {
    Map<String, String> state = snapshot(job);
    assertThat(state.get("status")).isEqualTo("COMPLETED");
    assertThat(state.get("attempts")).isEqualTo(Integer.toString(attempts));
    assertThat(state.get("lock_owner")).isNull();
  }

  private Map<String, String> snapshot(long job) throws Exception {
    try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement(
        "SELECT id,bundle_id,schedule_version,status,attempts,next_attempt_at,lock_owner,lock_until,last_error "
            + "FROM medication_schedule_outbox WHERE id=?")) {
      query.setLong(1, job);
      try (ResultSet rows = query.executeQuery()) {
        assertThat(rows.next()).isTrue();
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
          result.put(rows.getMetaData().getColumnLabel(i), rows.getString(i));
        }
        return result;
      }
    }
  }

  private void makeDue(long job) throws Exception {
    try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement(
        "UPDATE medication_schedule_outbox SET next_attempt_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND "
            + "WHERE id=? AND status='RETRY_WAITING'")) {
      update.setLong(1, job);
      assertThat(update.executeUpdate()).isEqualTo(1);
    }
  }

  private void awaitDatabaseTime(long job, String column, Duration maximum) throws Exception {
    if (!List.of("next_attempt_at", "lock_until").contains(column)) {
      throw new IllegalArgumentException("Unsupported clock field");
    }
    Instant start = Instant.now();
    Instant deadline = start.plus(maximum);
    int seconds = 0;
    while (Instant.now().isBefore(deadline)) {
      try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement(
          "SELECT " + column + " <= UTC_TIMESTAMP(6) FROM medication_schedule_outbox WHERE id=?")) {
        query.setLong(1, job);
        try (ResultSet result = query.executeQuery()) {
          assertThat(result.next()).isTrue();
          if (result.getBoolean(1)) {
            observe("real_clock_wait_completed", Map.of("jobId", job, "column", column,
                "elapsedMillis", Duration.between(start, Instant.now()).toMillis()));
            return;
          }
        }
      }
      if (seconds++ % 30 == 0) {
        System.out.println("FAULT_WAIT job=" + job + " field=" + column + " elapsed=" + (seconds - 1) + "s");
      }
      Thread.sleep(1000);
    }
    throw new AssertionError("Database clock deadline not reached: " + column);
  }

  private long count(String sql) throws Exception {
    try (Connection connection = connection(); var query = connection.createStatement(); var rows = query.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private Connection connection() throws Exception { return DriverManager.getConnection(jdbcUrl, "root", ""); }

  private void observe(String step, Map<?, ?> data) throws Exception {
    observations.add(Map.of("at", Instant.now().toString(), "step", step, "data", data));
    System.out.println("FAULT_STEP " + step);
    writeEvidence();
  }

  private final class Worker implements AutoCloseable {
    private final Process process;
    private final BufferedWriter commands;
    private final BlockingQueue<JsonNode> replies = new LinkedBlockingQueue<>();

    Worker() throws Exception {
      String classpath = Files.readString(Path.of(System.getProperty("outbox.fault.classpathFile")));
      Path args = evidence.resolve("worker-" + UUID.randomUUID() + ".args");
      Files.writeString(args, "-Xmx256m\n-Duser.timezone=UTC\n-cp\n\""
          + classpath.replace('\\', '/') + "\"\n" + OutboxFaultWorker.class.getName() + "\n");
      Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
      if (!Files.exists(java)) { java = Path.of(System.getProperty("java.home"), "bin", "java"); }
      ProcessBuilder builder = new ProcessBuilder(java.toString(), "@" + args.toAbsolutePath()).redirectErrorStream(true);
      builder.environment().put("OUTBOX_FAULT_RECEIVER", receiver.url());
      process = builder.start();
      commands = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
      Path log = evidence.resolve("worker-" + process.pid() + ".log");
      Thread.ofPlatform().daemon().start(() -> {
        try (var lines = process.inputReader(StandardCharsets.UTF_8);
            var output = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
          String line;
          while ((line = lines.readLine()) != null) {
            output.write(line);
            output.newLine();
            output.flush();
            if (line.startsWith("FAULT_JSON ")) { replies.add(JSON.readTree(line.substring(11))); }
          }
        } catch (Exception e) {
          replies.add(JSON.createObjectNode().put("error", e.getClass().getSimpleName()));
        }
      });
      JsonNode ready = replies.poll(60, TimeUnit.SECONDS);
      if (ready == null || !ready.path("ready").asBoolean()) {
        close();
        throw new AssertionError("Worker did not become ready. Inspect " + log);
      }
    }

    long pid() { return process.pid(); }

    void begin(Map<String, Object> command) throws Exception {
      commands.write(JSON.writeValueAsString(command));
      commands.newLine();
      commands.flush();
    }

    JsonNode command(Map<String, Object> command) throws Exception {
      begin(command);
      return await();
    }

    void ok(Map<String, Object> command) throws Exception { begin(command); awaitOk(); }

    void awaitOk() throws Exception { assertThat(await().path("ok").asBoolean()).isTrue(); }

    JsonNode await() throws Exception {
      JsonNode reply = replies.poll(30, TimeUnit.SECONDS);
      assertThat(reply).as("Worker reply; PID %s (see evidence log)", pid()).isNotNull();
      return reply;
    }

    void kill() throws Exception {
      process.destroyForcibly();
      assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
      assertThat(process.isAlive()).isFalse();
    }

    @Override
    public void close() throws Exception {
      if (process.isAlive()) {
        commands.close();
        if (!process.waitFor(5, TimeUnit.SECONDS)) { kill(); }
      }
    }
  }

  private static final class Receiver implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, JsonNode> accepted = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
    private final AtomicInteger duplicates = new AtomicInteger();
    private final AtomicInteger pushes = new AtomicInteger();
    private volatile String mode = "OK";
    private volatile CountDownLatch entered = new CountDownLatch(1);
    private volatile CountDownLatch release = new CountDownLatch(0);

    Receiver() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(executor);
      server.createContext("/tasks", this::register);
      server.createContext("/push", exchange -> {
        exchange.getRequestBody().readAllBytes();
        pushes.incrementAndGet();
        reply(exchange, 200);
      });
      server.start();
    }

    String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    int calls(String task) { return requests.getOrDefault(task, new AtomicInteger()).get(); }

    void hold() {
      entered = new CountDownLatch(1);
      release = new CountDownLatch(1);
      mode = "HOLD_BEFORE_ACCEPT";
    }

    private void register(HttpExchange exchange) throws IOException {
      try {
        JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
        String task = request.path("taskId").asText();
        requests.computeIfAbsent(task, ignored -> new AtomicInteger()).incrementAndGet();
        String behavior = mode;
        if ("FAIL".equals(behavior)) { reply(exchange, 503); return; }
        if ("HOLD_BEFORE_ACCEPT".equals(behavior)) {
          entered.countDown();
          if (!release.await(15, TimeUnit.SECONDS)) { reply(exchange, 503); return; }
          // 강제 종료 실험은 외부 부수 효과가 발생하기 전에 이 시도를 의도적으로 중단한다.
          if (!"HOLD_BEFORE_ACCEPT".equals(mode)) { exchange.close(); return; }
        }
        boolean duplicate = accepted.putIfAbsent(task, request) != null;
        if (duplicate) { duplicates.incrementAndGet(); }
        if ("DROP_AFTER_ACCEPT".equals(behavior)) { exchange.close(); return; }
        reply(exchange, duplicate ? 409 : 200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        exchange.close();
      }
    }

    private static void reply(HttpExchange exchange, int status) throws IOException {
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    }

    @Override
    public void close() {
      release.countDown();
      server.stop(0);
      executor.shutdownNow();
    }
  }
}
