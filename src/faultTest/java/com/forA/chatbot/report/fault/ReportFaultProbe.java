package com.forA.chatbot.report.fault;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import jdk.jfr.Event;
import jdk.jfr.Name;

/** 실행, 풀 점유, 지연 이벤트를 수집한다. SQL 원문과 문서 내용은 수집하지 않는다. */
final class ReportFaultProbe implements CommandListener {
  final ThreadLocal<Sample> current = new ThreadLocal<>();
  final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();
  final ConcurrentLinkedQueue<Map<String, Object>> events = new ConcurrentLinkedQueue<>();
  final AtomicInteger ids = new AtomicInteger();
  volatile long delayMs;
  volatile Gate gate;
  volatile String caseId = "setup";
  private final boolean detailed;

  ReportFaultProbe() { this(false); }
  ReportFaultProbe(boolean detailed) { this.detailed = detailed; }

  Sample begin(String kind) {
    Sample sample = new Sample(ids.incrementAndGet(), caseId, kind);
    current.set(sample);
    samples.add(sample);
    event(sample, "request_start", 0);
    return sample;
  }

  void finish(Sample sample, int status) {
    sample.status = status;
    sample.durationMs = millis(System.nanoTime() - sample.start);
    event(sample, "request_end", status);
    current.remove();
  }

  void event(Sample sample, String type, double value) {
    events.add(Map.of("at", Instant.now().toString(), "request", sample.id, "case", sample.caseId,
        "kind", sample.kind, "event", type, "value", value, "jdbcInUse", sample.inUse.get()));
  }

  @Override
  public void commandStarted(CommandStartedEvent event) {
    Sample sample = current.get();
    if (sample == null) {
      return;
    }
    String command = event.getCommandName();
    if (!List.of("find", "aggregate", "count", "distinct", "getMore").contains(command)) {
      return;
    }
    sample.mongo++;
    event(sample, "mongo_" + command, sample.mongo);
    Gate activeGate = gate;
    if (activeGate != null && !sample.gated && "report".equals(sample.kind)) {
      sample.gated = true;
      sample.heldAtGate = sample.inUse.get();
      event(sample, "mongo_gate_enter", sample.heldAtGate);
      activeGate.entered.countDown();
      try {
        if (!activeGate.release.await(15, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Mongo test gate was not released");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Mongo test gate interrupted", e);
      }
      event(sample, "mongo_gate_leave", 0);
    }
    if (delayMs > 0 && "report".equals(sample.kind)) {
      long start = System.nanoTime();
      sample.maxHeldDuringDelay = Math.max(sample.maxHeldDuringDelay, sample.inUse.get());
      event(sample, "mongo_delay_start", delayMs);
      try {
        TimeUnit.MILLISECONDS.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Mongo query-boundary delay interrupted", e);
      }
      sample.injectedDelayMs += millis(System.nanoTime() - start);
      event(sample, "mongo_delay_end", 0);
    }
  }

  @Override
  public void commandSucceeded(CommandSucceededEvent completed) {
    Sample sample = current.get();
    if (detailed && sample != null) {
      event(sample, "mongo_" + completed.getCommandName() + "_complete",
          completed.getElapsedTime(TimeUnit.NANOSECONDS) / 1_000_000.0);
    }
  }

  DataSource wrap(DataSource delegate) {
    return new AbstractDataSource() {
      @Override public Connection getConnection() throws SQLException {
        return borrow(delegate, null, null);
      }
      @Override public Connection getConnection(String user, String password) throws SQLException {
        return borrow(delegate, user, password);
      }
    };
  }

  private Connection borrow(DataSource source, String user, String password) throws SQLException {
    Sample sample = current.get();
    long start = System.nanoTime();
    Connection raw;
    try {
      raw = user == null ? source.getConnection() : source.getConnection(user, password);
    } catch (SQLException e) {
      if (sample != null) {
        sample.acquireMs.add(millis(System.nanoTime() - start));
        sample.acquireFailures++;
        event(sample, "jdbc_acquire_failed", millis(System.nanoTime() - start));
      }
      throw e;
    }
    if (sample == null) {
      return raw;
    }
    long borrowed = System.nanoTime();
    sample.acquireMs.add(millis(borrowed - start));
    sample.inUse.incrementAndGet();
    event(sample, "jdbc_borrow", millis(borrowed - start));
    AtomicBoolean closed = new AtomicBoolean();
    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
        new Class<?>[] {Connection.class}, (proxy, method, args) -> {
          if ("close".equals(method.getName()) && closed.compareAndSet(false, true)) {
            try {
              return measuredInvoke(raw, method, args, sample);
            } finally {
              double usage = millis(System.nanoTime() - borrowed);
              sample.usageMs.add(usage);
              sample.inUse.decrementAndGet();
              event(sample, "jdbc_return", usage);
            }
          }
          Object result = measuredInvoke(raw, method, args, sample);
          if (result instanceof Statement statement) {
            return wrapStatement(statement, sample);
          }
          return result;
        });
  }

  private Object wrapStatement(Statement raw, Sample sample) {
    Class<?> type = raw instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
    return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
      if (method.getName().startsWith("execute")) {
        sample.sql++;
        event(sample, "sql_execute", sample.sql);
      }
      return measuredInvoke(raw, method, args, sample);
    });
  }

  private Object measuredInvoke(Object target, Method method, Object[] args, Sample sample) throws Throwable {
    String operation = method.getName();
    if (!detailed || !(operation.startsWith("execute") || List.of("setReadOnly", "setAutoCommit", "getAutoCommit",
        "getTransactionIsolation", "setTransactionIsolation", "prepareStatement", "commit", "rollback", "close")
        .contains(operation))) {
      return invoke(target, method, args);
    }
    long start = System.nanoTime();
    event(sample, "jdbc_" + operation + "_start", 0);
    JdbcCall flight = new JdbcCall();
    flight.request = sample.id;
    flight.caseId = sample.caseId;
    flight.operation = operation;
    flight.begin();
    try {
      return invoke(target, method, args);
    } finally {
      flight.end();
      flight.commit();
      double duration = millis(System.nanoTime() - start);
      sample.jdbcCalls.add(Map.of("operation", operation, "durationMs", duration));
      event(sample, "jdbc_" + operation + "_complete", duration);
    }
  }

  @Name("foryn.JdbcCall")
  static final class JdbcCall extends Event {
    int request;
    String caseId;
    String operation;
  }

  private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  static double millis(long nanos) {
    return nanos / 1_000_000.0;
  }

  static final class Gate {
    final CountDownLatch entered;
    final CountDownLatch release = new CountDownLatch(1);
    Gate(int count) { entered = new CountDownLatch(count); }
  }

  static final class Sample {
    final int id;
    final String caseId;
    final String kind;
    final long start = System.nanoTime();
    final AtomicInteger inUse = new AtomicInteger();
    final List<Double> acquireMs = Collections.synchronizedList(new ArrayList<>());
    final List<Double> usageMs = Collections.synchronizedList(new ArrayList<>());
    final List<Map<String, Object>> jdbcCalls = Collections.synchronizedList(new ArrayList<>());
    int sql;
    int mongo;
    int status;
    int acquireFailures;
    int heldAtGate;
    int maxHeldDuringDelay;
    boolean gated;
    String failure = "";
    double durationMs;
    double injectedDelayMs;

    Sample(int id, String caseId, String kind) {
      this.id = id;
      this.caseId = caseId;
      this.kind = kind;
    }

    Map<String, Object> json() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("id", id);
      values.put("case", caseId);
      values.put("kind", kind);
      values.put("status", status);
      values.put("failureCause", failure);
      values.put("serverMs", durationMs);
      values.put("sqlExecutions", sql);
      values.put("mongoCommands", mongo);
      values.put("jdbcAcquireMs", acquireMs);
      values.put("jdbcUsageMs", usageMs);
      values.put("jdbcCalls", jdbcCalls);
      values.put("jdbcAcquireFailures", acquireFailures);
      values.put("jdbcStillInUse", inUse.get());
      values.put("jdbcHeldAtGate", heldAtGate);
      values.put("maxJdbcHeldDuringMongoDelay", maxHeldDuringDelay);
      values.put("injectedDelayMs", injectedDelayMs);
      return values;
    }
  }
}
