package com.forA.chatbot.report.fault;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.domain.ChatMessage;
import com.forA.chatbot.chat.domain.ChatSession;
import com.forA.chatbot.chat.domain.enums.ChatStep;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.report.fault.ReportFaultWorkload.Variant;
import com.forA.chatbot.user.domain.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** 실제 MySQL/Mongo/JPA/Hikari와 루프백 HTTP를 사용한다. 운영 HTTP·인증과 클라우드 서비스는 제외한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class ReportPoolFaultTest {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static final int REPORT_RATE = 16;
  private static final int MYSQL_RATE = 8;
  private final List<Map<String, Object>> summaries = new ArrayList<>();
  private final List<Map<String, Object>> gates = new ArrayList<>();
  private final List<Map<String, Object>> correctness = new ArrayList<>();
  private final List<Map<String, Object>> clientSamples = new ArrayList<>();
  // 플랫폼 스레드로 동기 드라이버 콜백의 가상 스레드 고정을 피한다.
  // 기본 설정에서는 지연된 리포트 요청을 최대 6 * 16 = 96개 전달한다.
  private final ExecutorService clients = Executors.newCachedThreadPool();
  private final ExecutorService handlers = Executors.newFixedThreadPool(160);
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(3)).executor(clients).build();
  private final Instant started = Instant.now();
  private final LocalDate seededDate = LocalDate.now();
  private AnnotationConfigApplicationContext context;
  private ReportFaultProbe probe;
  private HikariDataSource pool;
  private HttpServer server;
  private URI base;
  private Path evidence;
  private ReportFaultDiagnostics diagnostics;
  private long userId;
  private volatile Variant variant = Variant.D_BATCH_NO_TX;

  @BeforeAll
  void startDisposableEnvironment() throws Exception {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    String jdbc = ReportFaultSupport.requireJdbc(System.getenv("REPORT_FAULT_JDBC_URL"));
    ReportFaultSupport.requireMongo(System.getenv("REPORT_FAULT_MONGO_URI"));
    evidence = Path.of(System.getProperty("report.fault.evidence"),
        Instant.now().toEpochMilli() + "-" + UUID.randomUUID());
    Files.createDirectories(evidence);
    Flyway.configure().dataSource(jdbc, "root", "")
        .locations("filesystem:src/main/resources/db/migration").load().migrate();
    context = new AnnotationConfigApplicationContext(ReportFaultConfiguration.class);
    probe = context.getBean(ReportFaultProbe.class);
    pool = context.getBean(HikariDataSource.class);
    assertThat(context.getBean(UserRepository.class).count()).as("Use a fresh disposable database").isZero();
    assertThat(context.getBean(ChatSessionRepository.class).count()).isZero();
    assertThat(context.getBean(ChatMessageRepository.class).count()).isZero();
    seed();
    if ("1".equals(System.getenv("REPORT_FAULT_DIAGNOSE"))) {
      diagnostics = new ReportFaultDiagnostics(evidence, jdbc);
    }
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 256);
    server.setExecutor(handlers);
    server.createContext("/report", this::serve);
    server.createContext("/mysql", this::serve);
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("environment.json").toFile(), environment());
    System.out.println("REPORT_FAULT_EVIDENCE " + evidence.toAbsolutePath());
  }

  private void seed() {
    User user = context.getBean(UserRepository.class).saveAndFlush(User.builder()
        .email(UUID.randomUUID() + "@example.invalid").fullName("Synthetic Report Test")
        .providerType(ProviderType.APPLE).providerUserId(UUID.randomUUID().toString()).build());
    userId = user.getId();
    MedicationBundle bundle = context.getBean(MedicationBundleRepository.class).saveAndFlush(
        MedicationBundle.builder().user(user).bundleName("Synthetic")
            .dayOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
            .scheduledTime(LocalTime.NOON).build());
    for (int day = 0; day < 14; day++) {
      LocalDate date = seededDate.minusDays(day);
      context.getBean(MedicationLogRepository.class).saveAndFlush(MedicationLog.builder()
          .medicationBundle(bundle).date(date).isTaken(true).medCondition(1).build());
      for (int session = 0; session < 2; session++) {
        ChatSession saved = context.getBean(ChatSessionRepository.class).save(ChatSession.builder()
            .userId(userId).startedAt(date.atTime(12, session)).onboardingCompleted(true).build());
        context.getBean(ChatMessageRepository.class).save(ChatMessage.builder().sessionId(saved.getId())
            .senderType(ChatMessage.SenderType.USER).chatStep(ChatStep.EMOTION_SELECT.name())
            .responseCode(session == 0 ? "HAPPY" : "SADNESS").sentAt(date.atTime(12, session)).build());
      }
    }
  }

  private void serve(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if (!"GET".equals(exchange.getRequestMethod()) || !(path.equals("/report") || path.equals("/mysql"))) {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }
    String kind = path.substring(1);
    ReportFaultProbe.Sample sample = probe.begin(kind);
    int status = 200;
    byte[] body;
    try {
      Object response = "report".equals(kind)
          ? context.getBean(ReportFaultWorkload.class).report(variant, userId)
          : Map.of("exists", context.getBean(ReportFaultWorkload.class).mysqlUserExists(userId));
      body = JSON.writeValueAsBytes(response);
    } catch (Exception e) {
      status = 500;
      Throwable root = e;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      sample.failure = root.getClass().getSimpleName();
      body = JSON.writeValueAsBytes(Map.of("error", e.getClass().getSimpleName()));
    }
    probe.finish(sample, status);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    try (var output = exchange.getResponseBody()) {
      output.write(body);
    } finally {
      exchange.close();
    }
  }

  @Test @Order(1)
  void sameResponsesAndActualQueryReduction() throws Exception {
    List<JsonNode> responses = new ArrayList<>();
    for (Variant selected : Variant.values()) {
      select("query-" + selected, selected, 0);
      // 먼저 Hibernate 메타데이터, 쿼리 계획, JDBC 문, 드라이버 소켓을 예열한다.
      assertThat(call("report", System.nanoTime()).status()).isEqualTo(200);
      select("count-" + selected, selected, 0);
      ClientSample result = call("report", System.nanoTime());
      assertThat(result.status()).isEqualTo(200);
      responses.add(JSON.readTree(result.body()));
      ReportFaultProbe.Sample measured = caseSamples().getFirst();
      assertThat(measured.mongo).isEqualTo(selected.batched ? 2 : 42);
      assertThat(measured.sql).isPositive();
      assertThat(measured.inUse.get()).isZero();
      correctness.add(measured.json());
    }
    assertThat(responses).allSatisfy(response -> assertThat(response).isEqualTo(responses.getFirst()));
    assertThat((int) correctness.get(3).get("sqlExecutions"))
        .isLessThan((int) correctness.get(0).get("sqlExecutions"));
    save();
  }

  @Test @Order(2)
  void mongoWaitHoldsConnectionsOnlyWithBroadTransaction() throws Exception {
    // 두 경우에 같은 일괄 쿼리를 사용하여 트랜잭션 점유와 쿼리 수의 영향을 분리한다.
    for (Variant selected : List.of(Variant.C_BATCH_TX, Variant.D_BATCH_NO_TX)) {
      select("gate-" + selected, selected, 0);
      var gate = new ReportFaultProbe.Gate(pool.getMaximumPoolSize());
      probe.gate = gate;
      List<CompletableFuture<ClientSample>> reports = new ArrayList<>();
      try {
        for (int i = 0; i < pool.getMaximumPoolSize(); i++) {
          reports.add(CompletableFuture.supplyAsync(() -> call("report", System.nanoTime()), clients));
        }
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).as("all reports paused inside Mongo boundary").isTrue();
        int active = pool.getHikariPoolMXBean().getActiveConnections();
        ClientSample ordinary = call("mysql", System.nanoTime());
        assertThat(active).isEqualTo(selected.transaction ? pool.getMaximumPoolSize() : 0);
        assertThat(ordinary.status()).isEqualTo(selected.transaction ? 500 : 200);
        List<ReportFaultProbe.Sample> reportSamples = caseSamples().stream()
            .filter(sample -> sample.kind.equals("report")).toList();
        assertThat(reportSamples).allSatisfy(sample ->
            assertThat(sample.heldAtGate).isEqualTo(selected.transaction ? 1 : 0));
        gates.add(Map.of("variant", selected.name(), "activeDuringMongoPause", active,
            "mysqlHttpStatus", ordinary.status(), "mysqlClientMs", ordinary.clientMs(),
            "mysqlAcquireFailures", caseSamples().stream().mapToInt(sample -> sample.acquireFailures).sum()));
      } finally {
        gate.release.countDown();
        probe.gate = null;
        for (var future : reports) {
          assertThat(future.get(20, TimeUnit.SECONDS).status()).isEqualTo(200);
        }
        save();
      }
    }
  }

  @Test @Order(3)
  void compareMixedHttpLoadAtFixedArrivalRates() throws Exception {
    boolean diagnosis = diagnostics != null;
    int repetitions = setting("REPORT_FAULT_REPETITIONS", 3, 1, diagnosis ? 20 : 5);
    int seconds = setting("REPORT_FAULT_SECONDS", 6, 2, 30);
    int slowMs = setting("REPORT_FAULT_DELAY_MS", 150, 25, 500);
    for (int repetition = 1; repetition <= repetitions; repetition++) {
      List<Run> order = new ArrayList<>();
      for (Variant selected : Variant.values()) {
        if (diagnosis && selected != Variant.D_BATCH_NO_TX) {
          continue;
        }
        order.add(new Run(selected, 0));
        if (!diagnosis) {
          order.add(new Run(selected, slowMs));
        }
      }
      Collections.shuffle(order, new Random(245L + repetition));
      for (Run run : order) {
        String id = "mix-r" + repetition + "-" + run.variant() + "-delay" + run.delayMs();
        select(id, run.variant(), run.delayMs());
        List<ClientSample> results = runMixed(seconds);
        results.forEach(sample -> clientSamples.add(Map.of("case", id, "variant", run.variant().name(),
            "delayMs", run.delayMs(), "kind", sample.kind(), "status", sample.status(),
            "clientMs", sample.clientMs(), "scheduleLagMs", sample.scheduleLagMs())));
        for (String kind : List.of("report", "mysql")) {
          List<ClientSample> group = results.stream().filter(sample -> sample.kind().equals(kind)).toList();
          Map<String, Object> summary = summarize(id, repetition, run, kind, seconds, group);
          summaries.add(summary);
          assertThat(group).hasSize(seconds * (kind.equals("report") ? REPORT_RATE : MYSQL_RATE));
          assertThat((int) summary.get("serverRequests")).as("Every offered request reached the server")
              .isEqualTo(group.size());
          assertThat(group).allSatisfy(sample -> assertThat(sample.status()).as("HTTP transport completes").isNotZero());
        }
        assertThat(caseSamples()).allSatisfy(sample -> assertThat(sample.inUse.get()).isZero());
        if (diagnostics != null) {
          diagnostics.captureServerStatements();
        }
        save();
        System.out.println("REPORT_FAULT_CASE " + id + " " + JSON.writeValueAsString(
            summaries.subList(summaries.size() - 2, summaries.size())));
      }
    }
    // 회귀 방지 조건: 수정 후 Mongo 대기 중에는 JDBC 연결을 점유하지 않아야 한다.
    // HTTP 오류는 관측값이다. 이 장비에 정의되지 않은 무오류 서비스 목표를 적용하지 않는다.
    assertThat(probe.samples.stream().filter(sample -> sample.caseId.contains("D_BATCH_NO_TX")).toList())
        .allSatisfy(sample -> assertThat(sample.maxHeldDuringDelay).isZero());
    double hikariTimeouts = context.getBean(MeterRegistry.class)
        .get("hikaricp.connections.timeout").counter().count();
    assertThat(hikariTimeouts).as("Request instrumentation agrees with Hikari's timeout counter")
        .isEqualTo((double) probe.samples.stream().mapToInt(sample -> sample.acquireFailures).sum());
  }

  private List<ClientSample> runMixed(int seconds) throws Exception {
    List<CompletableFuture<ClientSample>> pending = new ArrayList<>();
    AtomicInteger maxActive = new AtomicInteger();
    AtomicInteger maxPending = new AtomicInteger();
    try (ScheduledExecutorService arrival = Executors.newScheduledThreadPool(2);
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor()) {
      sampler.scheduleAtFixedRate(() -> {
        maxActive.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max);
        maxPending.accumulateAndGet(pool.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
      }, 0, 5, TimeUnit.MILLISECONDS);
      long origin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
      for (String kind : List.of("report", "mysql")) {
        int rate = kind.equals("report") ? REPORT_RATE : MYSQL_RATE;
        for (int i = 0; i < seconds * rate; i++) {
          long planned = origin + TimeUnit.SECONDS.toNanos(1) * i / rate;
          CompletableFuture<ClientSample> future = new CompletableFuture<>();
          pending.add(future);
          arrival.schedule(() -> clients.submit(() -> future.complete(call(kind, planned))),
              Math.max(0, planned - System.nanoTime()), TimeUnit.NANOSECONDS);
        }
      }
      CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(90, TimeUnit.SECONDS);
      poolPeaks = Map.of("maxActive", maxActive.get(), "maxPending", maxPending.get());
      return pending.stream().map(CompletableFuture::join).toList();
    }
  }

  private Map<String, Integer> poolPeaks = Map.of();

  private ClientSample call(String kind, long planned) {
    long start = System.nanoTime();
    try {
      var result = http.send(HttpRequest.newBuilder(base.resolve("/" + kind))
          .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
      return new ClientSample(kind, result.statusCode(), ReportFaultProbe.millis(System.nanoTime() - start),
          ReportFaultProbe.millis(start - planned), result.body());
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return new ClientSample(kind, 0, ReportFaultProbe.millis(System.nanoTime() - start),
          ReportFaultProbe.millis(start - planned), e.getClass().getSimpleName());
    }
  }

  private void select(String id, Variant selected, long delay) {
    assertThat(LocalDate.now()).as("Do not cross midnight during a comparison").isEqualTo(seededDate);
    variant = selected;
    probe.caseId = id;
    probe.delayMs = delay;
  }

  private List<ReportFaultProbe.Sample> caseSamples() {
    return probe.samples.stream().filter(sample -> sample.caseId.equals(probe.caseId)).toList();
  }

  private Map<String, Object> summarize(String id, int repetition, Run run, String kind,
      int seconds, List<ClientSample> results) {
    List<ReportFaultProbe.Sample> serverSamples = caseSamples().stream()
        .filter(sample -> sample.kind.equals(kind)).toList();
    List<Double> acquire = serverSamples.stream().flatMap(sample -> sample.acquireMs.stream()).toList();
    List<Double> usage = serverSamples.stream().flatMap(sample -> sample.usageMs.stream()).toList();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("case", id);
    row.put("repetition", repetition);
    row.put("variant", run.variant().name());
    row.put("delayPerMongoCommandMs", run.delayMs());
    row.put("kind", kind);
    row.put("arrivalSeconds", seconds);
    row.put("requests", results.size());
    row.put("serverRequests", serverSamples.size());
    row.put("errors", results.stream().filter(sample -> sample.status() != 200).count());
    row.put("errorRate", results.stream().filter(sample -> sample.status() != 200).count() / (double) results.size());
    row.put("clientP95Ms", ReportFaultSupport.percentile(results.stream().map(ClientSample::clientMs).toList(), 0.95));
    row.put("scheduleLagP95Ms", ReportFaultSupport.percentile(results.stream().map(ClientSample::scheduleLagMs).toList(), 0.95));
    row.put("jdbcAcquireP95Ms", ReportFaultSupport.percentile(acquire, 0.95));
    row.put("jdbcUsageP95Ms", ReportFaultSupport.percentile(usage, 0.95));
    row.put("jdbcUsageMeanMs", usage.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    row.put("jdbcAcquireFailures", serverSamples.stream().mapToInt(sample -> sample.acquireFailures).sum());
    row.put("failureCauses", serverSamples.stream().filter(sample -> !sample.failure.isEmpty())
        .collect(java.util.stream.Collectors.groupingBy(sample -> sample.failure, java.util.stream.Collectors.counting())));
    row.put("sqlPerRequest", serverSamples.stream().mapToInt(sample -> sample.sql).sum() / (double) results.size());
    row.put("mongoPerRequest", serverSamples.stream().mapToInt(sample -> sample.mongo).sum() / (double) results.size());
    row.put("poolPeaks", poolPeaks);
    row.put("maxJdbcHeldDuringMongoDelay", serverSamples.stream().mapToInt(sample -> sample.maxHeldDuringDelay).max().orElse(0));
    return row;
  }

  private Map<String, Object> environment() throws Exception {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("startedUtc", started.toString());
    values.put("java", System.getProperty("java.version"));
    values.put("os", System.getProperty("os.name"));
    values.put("processors", Runtime.getRuntime().availableProcessors());
    values.put("httpThreads", "160 platform handler threads; cached platform client executor");
    values.put("seedEndDate", seededDate.toString());
    try (var connection = pool.getConnection()) {
      values.put("mysqlVersion", connection.getMetaData().getDatabaseProductVersion());
    }
    values.put("hikariVersion", HikariDataSource.class.getPackage().getImplementationVersion());
    values.put("poolSize", pool.getMaximumPoolSize());
    values.put("connectionTimeoutMs", pool.getConnectionTimeout());
    values.put("validationTimeoutMs", pool.getValidationTimeout());
    values.put("validationQuery", pool.getConnectionTestQuery());
    values.put("jdbcDriverVersion", com.mysql.cj.jdbc.Driver.class.getPackage().getImplementationVersion());
    values.put("autoCommit", pool.isAutoCommit());
    values.put("osiv", "absent: minimal service context, identical for all variants");
    values.put("boundary", "loopback HTTP fixture routes, real production weekly service and real database repositories");
    values.put("delayInjection", "client CommandListener before each real read command; not server/network latency");
    values.put("comparison", "historical repeated algorithm reconstructed from P0-A record; not historical source revision");
    values.put("seed", Map.of("days", 14, "sessions", 28, "messages", 28, "medicationLogs", 14));
    values.put("rates", Map.of("reportPerSecond", REPORT_RATE, "mysqlPerSecond", MYSQL_RATE));
    values.put("orderSeed", 245);
    values.put("percentile", "nearest-rank; includes failures; do not average per-run p95 values");
    Map<String, String> hashes = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(Path.of("src/faultTest/java/com/forA/chatbot/report/fault"))) {
      for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
        hashes.put(file.toString(), sha256(file));
      }
    }
    for (String file : List.of("build.gradle", "src/main/java/com/forA/chatbot/report/service/ReportService.java",
        "src/main/java/com/forA/chatbot/report/service/EmotionScoreCalculator.java",
        "src/main/java/com/forA/chatbot/report/service/ReportMetricsRecorder.java",
        "src/main/java/com/forA/chatbot/report/converter/ReportConverter.java",
        "src/main/java/com/forA/chatbot/chat/repository/ChatSessionRepository.java",
        "src/main/java/com/forA/chatbot/chat/repository/ChatMessageRepository.java",
        "src/main/java/com/forA/chatbot/medications/repository/MedicationLogRepository.java",
        "scripts/report_fault.py")) {
      hashes.put(file, sha256(Path.of(file)));
    }
    values.put("sourceSha256", hashes);
    return values;
  }

  private static String sha256(Path file) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
  }

  private void save() throws Exception {
    if (evidence == null || probe == null) {
      return;
    }
    JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("summary.json").toFile(), summaries);
    JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("gates.json").toFile(), gates);
    JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("query-counts.json").toFile(), correctness);
    Files.write(evidence.resolve("requests.ndjson"), probe.samples.stream()
        .map(sample -> encode(sample.json())).toList(), StandardCharsets.UTF_8);
    Files.write(evidence.resolve("events.ndjson"), probe.events.stream().map(ReportPoolFaultTest::encode).toList(),
        StandardCharsets.UTF_8);
    Files.write(evidence.resolve("clients.ndjson"), clientSamples.stream().map(ReportPoolFaultTest::encode).toList(),
        StandardCharsets.UTF_8);
    List<Map<String, Object>> metrics = context.getBean(MeterRegistry.class).getMeters().stream()
        .filter(meter -> meter.getId().getName().startsWith("hikaricp."))
        .map(meter -> {
          Map<String, Object> metric = new LinkedHashMap<>();
          metric.put("name", meter.getId().getName());
          metric.put("tags", meter.getId().getTags().toString());
          List<Map<String, Object>> measurements = new ArrayList<>();
          meter.measure().forEach(value -> measurements.add(Map.of("statistic", value.getStatistic().name(), "value", value.getValue())));
          metric.put("measurements", measurements);
          return metric;
        }).toList();
    JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("hikari-metrics.json").toFile(), metrics);
  }

  private static String encode(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static int setting(String name, int fallback, int min, int max) {
    String raw = System.getenv(name);
    int value = raw == null ? fallback : Integer.parseInt(raw);
    if (value < min || value > max) {
      throw new IllegalArgumentException(name + " out of local experiment bounds");
    }
    return value;
  }

  @AfterAll
  void close() throws Exception {
    if (probe != null && probe.gate != null) {
      probe.gate.release.countDown();
    }
    if (server != null) {
      server.stop(0);
    }
    http.close();
    handlers.close();
    clients.close();
    try {
      save();
      if (diagnostics != null) {
        diagnostics.close();
      }
    } finally {
      if (context != null) {
        context.close();
      }
    }
  }

  private record Run(Variant variant, long delayMs) {}
  private record ClientSample(String kind, int status, double clientMs, double scheduleLagMs, String body) {}
}
