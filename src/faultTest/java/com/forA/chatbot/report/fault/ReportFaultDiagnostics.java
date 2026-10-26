package com.forA.chatbot.report.fault;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** 허용 이벤트를 명시한다. 환경변수, 시스템 속성, SQL 원문, 문서 내용은 제외한다. */
final class ReportFaultDiagnostics implements AutoCloseable {
  private final Recording recording = new Recording();
  private final Path root;
  private final String jdbc;
  private final Map<String, Map<String, Object>> statements = new LinkedHashMap<>();
  private static final ObjectMapper JSON = new ObjectMapper();

  ReportFaultDiagnostics(Path root, String jdbc) throws Exception {
    this.root = root;
    this.jdbc = jdbc;
    try (var connection = DriverManager.getConnection(jdbc, "root", ""); var sql = connection.createStatement()) {
      sql.executeUpdate("UPDATE performance_schema.setup_consumers SET ENABLED='YES' "
          + "WHERE NAME IN ('events_statements_current','events_statements_history_long')");
    }
    for (String name : List.of("jdk.SocketRead", "jdk.SocketWrite", "jdk.ThreadPark", "jdk.JavaMonitorEnter",
        "jdk.GCPhasePause", "jdk.SafepointBegin", "jdk.SafepointStateSynchronization", "jdk.ExecuteVMOperation",
        "foryn.JdbcCall")) {
      recording.enable(name).withThreshold(Duration.ofMillis(10)).withStackTrace();
    }
    recording.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(100));
    recording.enable("jdk.GarbageCollection");
    recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
    recording.start();
  }

  void captureServerStatements() throws Exception {
    try (var connection = DriverManager.getConnection(jdbc, "root", ""); var sql = connection.createStatement()) {
      try (var rows = sql.executeQuery("SELECT THREAD_ID, EVENT_ID, EVENT_NAME, "
          + "TIMER_START, TIMER_WAIT / 1000000000.0 duration_ms, LOCK_TIME / 1000000000.0 lock_ms, "
          + "ROWS_EXAMINED, ROWS_SENT, MYSQL_ERRNO, "
          + "CASE WHEN DIGEST_TEXT LIKE 'SET%TRANSACTION%' THEN 'SET_TRANSACTION' "
          + "WHEN DIGEST_TEXT LIKE 'SET%AUTOCOMMIT%' THEN 'SET_AUTOCOMMIT' "
          + "WHEN TRIM(DIGEST_TEXT) = 'SELECT ?' THEN 'VALIDATION_SELECT_ONE' "
          + "WHEN DIGEST_TEXT LIKE 'SELECT%' THEN 'SELECT' ELSE EVENT_NAME END operation "
          + "FROM performance_schema.events_statements_history_long "
          + "WHERE CURRENT_SCHEMA = 'report_fault_local' "
          + "AND (DIGEST_TEXT IS NULL OR DIGEST_TEXT NOT LIKE '%performance_schema%') "
          + "AND THREAD_ID <> (SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID = CONNECTION_ID())")) {
        while (rows.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (String column : List.of("THREAD_ID", "EVENT_ID", "EVENT_NAME", "TIMER_START", "duration_ms",
              "lock_ms", "ROWS_EXAMINED", "ROWS_SENT", "MYSQL_ERRNO", "operation")) {
            row.put(column, rows.getObject(column));
          }
          statements.put(row.get("THREAD_ID") + "/" + row.get("EVENT_ID"), row);
        }
      }
    }
    JSON.writerWithDefaultPrettyPrinter().writeValue(root.resolve("mysql-statements.json").toFile(), statements.values());
  }

  @Override
  public void close() throws Exception {
    recording.stop();
    Path file = root.resolve("diagnosis.jfr");
    recording.dump(file);
    recording.close();
    List<Map<String, Object>> events = new ArrayList<>();
    try (RecordingFile input = new RecordingFile(file)) {
      while (input.hasMoreEvents()) {
        RecordedEvent event = input.readEvent();
        String type = event.getEventType().getName();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("at", event.getStartTime().toString());
        row.put("durationMs", event.getDuration().toNanos() / 1_000_000.0);
        RecordedThread thread = event.getThread();
        row.put("thread", thread == null ? "" : thread.getJavaName());
        if (type.equals("foryn.JdbcCall")) {
          row.put("request", event.getInt("request"));
          row.put("case", event.getString("caseId"));
          row.put("operation", event.getString("operation"));
        }
        if (type.equals("jdk.CPULoad")) {
          row.put("jvmUser", event.getFloat("jvmUser"));
          row.put("jvmSystem", event.getFloat("jvmSystem"));
          row.put("machineTotal", event.getFloat("machineTotal"));
        }
        if (event.getStackTrace() != null) {
          row.put("stack", event.getStackTrace().getFrames().stream().limit(20)
              .map(frame -> frame.getMethod().getType().getName() + "." + frame.getMethod().getName()).toList());
        }
        events.add(row);
      }
    }
    JSON.writerWithDefaultPrettyPrinter().writeValue(root.resolve("jfr-events.json").toFile(), events);
  }
}
