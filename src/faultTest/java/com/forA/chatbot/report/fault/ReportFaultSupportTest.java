package com.forA.chatbot.report.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ReportFaultSupportTest {
  @Test
  void validationDeadlineIsShorterThanAcquisitionDeadline() {
    var config = ReportFaultConfiguration.poolSettings("jdbc:mysql://127.0.0.1:13306/report_fault_local"
        + "?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC", new SimpleMeterRegistry());
    assertThat(config.getConnectionTimeout()).isEqualTo(300);
    assertThat(config.getValidationTimeout()).isEqualTo(250).isLessThan(config.getConnectionTimeout());
  }

  @Test
  void acceptsOnlyDisposableLoopbackDatabases() {
    String jdbc = "jdbc:mysql://127.0.0.1:13306/report_fault_local"
        + "?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC";
    assertThat(ReportFaultSupport.requireJdbc(jdbc)).isEqualTo(jdbc);
    assertThat(ReportFaultSupport.requireMongo("mongodb://127.0.0.1:27018/report_fault_local"))
        .endsWith("/report_fault_local");
    for (String invalid : List.of("jdbc:mysql://db:3306/report_fault_local",
        "jdbc:mysql://127.0.0.1:3306/foryn", jdbc + "&allowLoadLocalInfile=true")) {
      assertThatIllegalArgumentException().isThrownBy(() -> ReportFaultSupport.requireJdbc(invalid));
    }
    for (String invalid : List.of("mongodb://remote/report_fault_local",
        "mongodb://127.0.0.1:27017/foryn", "mongodb://127.0.0.1:27017/report_fault_local?tls=false")) {
      assertThatIllegalArgumentException().isThrownBy(() -> ReportFaultSupport.requireMongo(invalid));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> ReportFaultSupport.requireJdbc(null));
    assertThatIllegalArgumentException().isThrownBy(() -> ReportFaultSupport.requireMongo(null));
  }

  @Test
  void usesNearestRankPercentilesAndKeepsFailureSamples() {
    assertThat(ReportFaultSupport.percentile(List.of(10.0, 50.0, 20.0, 40.0, 30.0), 0.95))
        .isEqualTo(50.0);
    assertThat(ReportFaultSupport.percentile(List.of(), 0.95)).isZero();
    assertThat(ReportFaultSupport.percentile(List.of(7.0), 0.95)).isEqualTo(7.0);
  }

  @Test
  void countsExecutedSqlAndConnectionOwnershipWithRealJdbc() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:report_probe;DB_CLOSE_DELAY=-1");
    config.setMaximumPoolSize(1);
    try (HikariDataSource pool = new HikariDataSource(config)) {
      ReportFaultProbe probe = new ReportFaultProbe(true);
      var sample = probe.begin("mysql");
      try (var connection = probe.wrap(pool).getConnection();
          var statement = connection.prepareStatement("SELECT 1")) {
        assertThat(sample.inUse.get()).isEqualTo(1);
        connection.setReadOnly(true);
        assertThat(sample.sql).as("Preparing a statement is not a query execution").isZero();
        try (var rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
        }
        assertThat(sample.sql).isEqualTo(1);
        try (var rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
        }
        assertThat(sample.sql).as("Reusing a prepared statement executes a second query").isEqualTo(2);
      }
      probe.finish(sample, 200);
      assertThat(sample.inUse.get()).isZero();
      assertThat(sample.usageMs).hasSize(1);
      assertThat(sample.acquireMs).hasSize(1);
      assertThat(sample.jdbcCalls).anySatisfy(call -> assertThat(call.get("operation")).isEqualTo("setReadOnly"));
      assertThat(sample.jdbcCalls).anySatisfy(call -> assertThat(call.get("operation")).isEqualTo("executeQuery"));
      assertThat(probe.current.get()).isNull();
    }
  }
}
