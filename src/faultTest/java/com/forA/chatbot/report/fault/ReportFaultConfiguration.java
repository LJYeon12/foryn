package com.forA.chatbot.report.fault;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.report.converter.ReportConverter;
import com.forA.chatbot.report.service.EmotionScoreCalculator;
import com.forA.chatbot.report.service.ReportMetricsRecorder;
import com.forA.chatbot.report.service.ReportService;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 최소한의 실제 JPA/Mongo 문맥이며 앱 리소스, 클라우드 클라이언트, MVC, OSIV는 제외한다. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = {"com.forA.chatbot.auth.repository", "com.forA.chatbot.medications.repository"})
class ReportFaultConfiguration {
  @Bean ReportFaultProbe probe() { return new ReportFaultProbe("1".equals(System.getenv("REPORT_FAULT_DIAGNOSE"))); }
  @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }

  @Bean(destroyMethod = "close")
  HikariDataSource pool(MeterRegistry registry) {
    return new HikariDataSource(poolSettings(
        ReportFaultSupport.requireJdbc(System.getenv("REPORT_FAULT_JDBC_URL")), registry));
  }

  static HikariConfig poolSettings(String jdbc, MeterRegistry registry) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(ReportFaultSupport.requireJdbc(jdbc));
    config.setUsername("root");
    config.setPassword("");
    config.setPoolName("report-fault");
    config.setMaximumPoolSize(4);
    config.setMinimumIdle(4);
    config.setConnectionTimeout(300);
    config.setValidationTimeout(250);
    // JDBC isValid는 초 단위다. Connector/J의 연결 확인은 최소 1초로 바꾼다.
    // 이 짧은 로컬 실험에서는 고정 쿼리로 Hikari의 밀리초 단위 네트워크 제한 시간을 유지한다.
    config.setConnectionTestQuery("SELECT 1");
    config.setMetricRegistry(registry);
    return config;
  }

  @Bean @Primary
  DataSource dataSource(HikariDataSource pool, ReportFaultProbe probe) { return probe.wrap(pool); }

  @Bean
  LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(source);
    factory.setPackagesToScan("com.forA.chatbot");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.jdbc.time_zone", "UTC",
        "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
    return factory;
  }

  @Bean
  PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
    return new JpaTransactionManager(factory);
  }

  @Bean(destroyMethod = "close")
  MongoClient mongoClient(ReportFaultProbe probe) {
    ConnectionString connection = new ConnectionString(ReportFaultSupport.requireMongo(
        System.getenv("REPORT_FAULT_MONGO_URI")));
    return MongoClients.create(MongoClientSettings.builder().applyConnectionString(connection)
        .applyToConnectionPoolSettings(builder -> builder.maxSize(256).maxWaitTime(15, TimeUnit.SECONDS))
        .applyToClusterSettings(builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
        .addCommandListener(probe).build());
  }

  @Bean
  MongoTemplate mongoTemplate(MongoClient client) {
    String database = new ConnectionString(System.getenv("REPORT_FAULT_MONGO_URI")).getDatabase();
    return new MongoTemplate(client, database);
  }

  @Bean ChatSessionRepository sessions(MongoTemplate template) {
    return new MongoRepositoryFactory(template).getRepository(ChatSessionRepository.class);
  }

  @Bean ChatMessageRepository messages(MongoTemplate template) {
    return new MongoRepositoryFactory(template).getRepository(ChatMessageRepository.class);
  }

  @Bean ReportConverter converter() { return new ReportConverter(); }
  @Bean ReportMetricsRecorder reportMetrics(MeterRegistry registry) { return new ReportMetricsRecorder(registry); }

  @Bean
  EmotionScoreCalculator calculator(MedicationLogRepository logs, ChatSessionRepository sessions,
      ChatMessageRepository messages, ReportMetricsRecorder metrics) {
    return new EmotionScoreCalculator(logs, sessions, messages, null, metrics);
  }

  @Bean
  ReportService currentReport(ReportConverter converter, ReportMetricsRecorder metrics, EmotionScoreCalculator calculator) {
    // 주간 감정은 다른 도메인을 사용하지 않는다. 등록하지 않은 클라이언트는 외부 서비스를 호출할 수 없다.
    return new ReportService(null, null, null, converter, metrics, calculator, null, null, null, null);
  }

  @Bean
  ReportFaultWorkload workload(ReportService report, UserRepository users,
      ChatSessionRepository sessions, ChatMessageRepository messages,
      MedicationLogRepository logs, ReportConverter converter, PlatformTransactionManager manager) {
    return new ReportFaultWorkload(report, users, sessions, messages, logs, converter, manager);
  }
}
