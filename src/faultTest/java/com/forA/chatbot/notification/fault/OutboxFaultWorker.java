package com.forA.chatbot.notification.fault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.medications.converter.MedicationConverter;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationScheduleOutbox;
import com.forA.chatbot.medications.dto.MedicationRequestDto;
import com.forA.chatbot.medications.dto.MedicationUpdateRequestDto;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationScheduleOutboxRepository;
import com.forA.chatbot.medications.service.MedicationLogSaver;
import com.forA.chatbot.medications.service.MedicationScheduleOutboxWriter;
import com.forA.chatbot.medications.service.MedicationService;
import com.forA.chatbot.medications.service.MedicationStreakService;
import com.forA.chatbot.notification.client.CloudTasksClient;
import com.forA.chatbot.notification.converter.NotificationConverter;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.notification.service.MedicationOutboxDispatcher;
import com.forA.chatbot.notification.service.MedicationSchedulerService;
import com.forA.chatbot.notification.service.NotificationSendLogService;
import com.forA.chatbot.notification.service.NotificationService;
import com.forA.chatbot.user.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 별도 JVM에서 실제 애플리케이션 서비스와 JPA를 사용한다. 앱 설정과 클라우드 자격증명은 불러오지 않는다. */
public final class OutboxFaultWorker {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public static void main(String[] args) throws Exception {
    LocalTaskTransport.requireDisposableDatabase(System.getenv("OUTBOX_FAULT_JDBC_URL"));
    try (var context = new AnnotationConfigApplicationContext(WorkerConfiguration.class);
        var commands = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      emit(Map.of("ready", true, "pid", ProcessHandle.current().pid()));
      String line;
      while ((line = commands.readLine()) != null) {
        JsonNode command = JSON.readTree(line);
        try {
          emit(execute(context, command));
        } catch (Exception e) {
          e.printStackTrace(System.err);
          emit(Map.of("error", e.getClass().getSimpleName()));
        }
      }
    }
  }

  private static Object execute(AnnotationConfigApplicationContext ctx, JsonNode command) {
    var dispatcher = ctx.getBean(MedicationOutboxDispatcher.class);
    var plans = ctx.getBean(MedicationBundleRepository.class);
    var jobs = ctx.getBean(MedicationScheduleOutboxRepository.class);
    var service = ctx.getBean(MedicationService.class);
    long id = command.path("jobId").asLong();
    switch (command.path("operation").asText()) {
      case "seed":
      case "rollback":
        return new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)).execute(status -> {
          User user = ctx.getBean(UserRepository.class).saveAndFlush(User.builder()
              .email(UUID.randomUUID() + "@example.invalid").fullName("Synthetic Fault Test")
              .providerType(ProviderType.APPLE).providerUserId(UUID.randomUUID().toString()).build());
          Long bundleId = service.createMedicationPlan(user.getId(), MedicationRequestDto.builder()
              .name("Synthetic Plan").takeTime("23:59").typeTags(List.of("synthetic"))
              .takeDays(Arrays.stream(java.time.DayOfWeek.values()).map(Enum::name).toList()).build()).getId();
          ctx.getBean(DeviceTokenRepository.class).saveAndFlush(DeviceToken.builder()
              .user(user).deviceToken("synthetic-" + UUID.randomUUID()).isPushEnabled(true).build());
          MedicationScheduleOutbox job = jobs.findByBundleIdAndScheduledDateAndScheduleVersion(
              bundleId, LocalDate.now(KST), 0).orElseThrow();
          if ("rollback".equals(command.path("operation").asText())) {
            throw new IllegalStateException("Intentional transaction rollback");
          }
          return Map.of("bundleId", bundleId, "userId", user.getId(), "jobId", job.getId(),
              "taskId", job.taskId(), "status", job.getStatus().name());
        });
      case "dispatch":
        dispatcher.dispatchOne(id);
        break;
      case "recover":
        dispatcher.recoverAndDispatch();
        break;
      case "retry":
        dispatcher.retryFailed(id);
        break;
      case "update":
        MedicationBundle plan = plans.findById(command.path("bundleId").asLong()).orElseThrow();
        service.updateMedicationPlan(plan.getUser().getId(), plan.getId(),
            MedicationUpdateRequestDto.builder().name("Changed Synthetic").build());
        break;
      case "delete":
        MedicationBundle deleted = plans.findById(command.path("bundleId").asLong()).orElseThrow();
        service.deleteMedicationPlan(deleted.getUser().getId(), deleted.getId());
        break;
      case "deliver":
        // 비동기 이벤트 리스너를 등록하지 않고 생성하여 테스트에서 해당 실행 신호를 의도적으로 누락한다.
        new MedicationSchedulerService(plans, ctx.getBean(DeviceTokenRepository.class),
            ctx.getBean(NotificationService.class), ctx.getBean(NotificationSendLogService.class),
            ctx.getBean(MedicationScheduleOutboxWriter.class), dispatcher, ctx.getBean(MeterRegistry.class),
            ctx.getBean(NotificationConverter.class)).handleSendTask(command.path("bundleId").asLong(),
                LocalDate.now(KST), command.path("version").asLong());
        break;
      default:
        throw new IllegalArgumentException("Unknown local test operation");
    }
    return Map.of("ok", true, "pid", ProcessHandle.current().pid());
  }

  private static void emit(Object value) throws Exception {
    System.out.println("FAULT_JSON " + JSON.writeValueAsString(value));
    System.out.flush();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EnableJpaAuditing
  @EnableJpaRepositories(basePackages = {"com.forA.chatbot.medications.repository",
      "com.forA.chatbot.auth.repository", "com.forA.chatbot.notification.repository"})
  @Import({MedicationService.class, MedicationConverter.class, MedicationScheduleOutboxWriter.class,
      MedicationOutboxDispatcher.class, MedicationLogSaver.class, MedicationStreakService.class,
      NotificationSendLogService.class, NotificationConverter.class})
  static class WorkerConfiguration {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(LocalTaskTransport.requireDisposableDatabase(
          System.getenv("OUTBOX_FAULT_JDBC_URL")), "root", "");
    }

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

    @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }

    @Bean
    CloudTasksClient cloudTasksClient() {
      return new LocalTaskTransport(URI.create(System.getenv("OUTBOX_FAULT_RECEIVER")));
    }

    @Bean
    NotificationService notificationService(CloudTasksClient transport) {
      return new NotificationService(null, null, null, null, null) {
        @Override
        public boolean sendNotification(FcmNotificationRequestDto request) {
          // 가상 데이터라도 토큰이나 메시지 내용을 테스트 수신기로 보내지 않는다.
          ((LocalTaskTransport) transport).push();
          return true;
        }
      };
    }
  }
}
