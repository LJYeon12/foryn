package com.forA.chatbot.chat.fault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.chat.component.BehavioralSkillProvider;
import com.forA.chatbot.chat.component.ChatStepHandlerProvider;
import com.forA.chatbot.chat.component.impl.ActionFeedbackStepHandler;
import com.forA.chatbot.chat.component.impl.BirthYearStepHandler;
import com.forA.chatbot.chat.component.impl.ChatEndStepHandler;
import com.forA.chatbot.chat.component.impl.EmotionStepHandler;
import com.forA.chatbot.chat.component.impl.DisorderStepHandler;
import com.forA.chatbot.chat.component.impl.SymptomStepHandler;
import com.forA.chatbot.chat.component.impl.GenderStepHandler;
import com.forA.chatbot.chat.component.impl.JobStepHandler;
import com.forA.chatbot.chat.component.impl.SkillConfirmStepHandler;
import com.forA.chatbot.chat.component.impl.SkillSelectStepHandler;
import com.forA.chatbot.chat.converter.ChatConverter;
import com.forA.chatbot.chat.repository.ChatMessageRepository;
import com.forA.chatbot.chat.repository.ChatOperationRepository;
import com.forA.chatbot.chat.repository.ChatSessionRepository;
import com.forA.chatbot.chat.service.ChatAccountDeletion;
import com.forA.chatbot.chat.service.ChatAfterCommitActions;
import com.forA.chatbot.chat.service.ChatAiService;
import com.forA.chatbot.chat.service.ChatOperationService;
import com.forA.chatbot.chat.service.ChatProfileWriter;
import com.forA.chatbot.chat.service.ChatRecoveryService;
import com.forA.chatbot.chat.service.ChatSaveHelper;
import com.forA.chatbot.chat.service.ChatService;
import com.forA.chatbot.chat.service.MongoChatWriter;
import com.forA.chatbot.config.ChatTransactionConfiguration;
import com.forA.chatbot.config.migration.V15__Prepare_chat_recovery;
import com.forA.chatbot.notification.scheduler.ChatNotificationScheduler;
import com.forA.chatbot.subscriptions.repository.SubscriptionRepository;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Boot, 애플리케이션 리소스, 클라우드, MVC, OSIV 없이 구성한다. 저장소는 모두 실제로 사용한다. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.forA.chatbot.auth.repository")
@Import({ChatTransactionConfiguration.class, ChatProfileWriter.class,
    ChatRecoveryService.class, ChatOperationService.class, ChatSaveHelper.class, ChatService.class,
    ChatAccountDeletion.class, ChatAfterCommitActions.class})
class ChatFaultConfiguration {
    @Bean ChatFaultProbe probe() { return new ChatFaultProbe(); }
    @Bean MeterRegistry metrics() { return new SimpleMeterRegistry(); }
    @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }

    static String requireJdbc(String value) {
        if (value == null || !value.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/chat_fault_local"
                + "\\?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC")) {
            throw new IllegalArgumentException("Disposable loopback chat MySQL required");
        }
        return value;
    }

    static String requireMongo(String value) {
        if (value == null || !value.matches("mongodb://127\\.0\\.0\\.1:[0-9]+/chat_fault_local"
                + "\\?replicaSet=chat-test&directConnection=true")) {
            throw new IllegalArgumentException("Disposable loopback chat Mongo replica set required");
        }
        return value;
    }

    @Bean(destroyMethod = "close") HikariDataSource pool() {
        var pool = new HikariDataSource();
        pool.setJdbcUrl(requireJdbc(System.getenv("CHAT_FAULT_JDBC_URL")));
        pool.setUsername("root");
        pool.setPassword("");
        pool.setMaximumPoolSize(6);
        pool.setConnectionTimeout(5000);
        return pool;
    }
    @Bean @Primary DataSource source(HikariDataSource pool, ChatFaultProbe probe) { return probe.wrap(pool); }
    @Bean(destroyMethod = "close") MongoClient mongoClient() {
        return MongoClients.create(MongoClientSettings.builder().applyConnectionString(
                new ConnectionString(requireMongo(System.getenv("CHAT_FAULT_MONGO_URI"))))
            .applyToClusterSettings(value -> value.serverSelectionTimeout(3, TimeUnit.SECONDS))
            .writeConcern(WriteConcern.MAJORITY).build());
    }
    @Bean MongoDatabaseFactory mongoDatabaseFactory(MongoClient client) {
        return new SimpleMongoClientDatabaseFactory(client, "chat_fault_local");
    }
    @Bean MongoTemplate mongoTemplate(MongoDatabaseFactory factory) { return Mockito.spy(new MongoTemplate(factory)); }
    @Bean(initMethod = "migrate") Flyway flyway(DataSource source, MongoTemplate mongo) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration")
            .javaMigrations(new V15__Prepare_chat_recovery(mongo)).load();
    }
    @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("com.forA.chatbot");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.jdbc.time_zone", "UTC",
            "hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        return factory;
    }
    @Bean MongoChatWriter mongoWriter(MongoTemplate mongo,
            @Qualifier("mongoTransactionManager") PlatformTransactionManager manager) {
        return Mockito.spy(new MongoChatWriter(mongo, manager));
    }
    @Bean ChatOperationRepository operations(DataSource source,
            @Qualifier("transactionManager") PlatformTransactionManager manager) {
        return Mockito.spy(new ChatOperationRepository(source, manager, 300));
    }
    @Bean ChatSessionRepository sessions(MongoTemplate mongo) {
        return new MongoRepositoryFactory(mongo).getRepository(ChatSessionRepository.class);
    }
    @Bean ChatMessageRepository messages(MongoTemplate mongo) {
        return new MongoRepositoryFactory(mongo).getRepository(ChatMessageRepository.class);
    }
    @Bean SubscriptionRepository subscriptions() { return Mockito.mock(SubscriptionRepository.class); }
    @Bean ChatNotificationScheduler notifications() { return Mockito.mock(ChatNotificationScheduler.class); }
    @Bean ChatConverter converter(ObjectMapper mapper) { return new ChatConverter(mapper); }
    @Bean com.forA.chatbot.user.service.UserService accountService(com.forA.chatbot.auth.repository.UserRepository users,
            ChatService chat) {
        var resetUsers = Mockito.mock(com.forA.chatbot.auth.repository.UserRepository.class,
            org.mockito.AdditionalAnswers.delegatesTo(users));
        return new com.forA.chatbot.user.service.UserService(resetUsers,
            Mockito.mock(com.forA.chatbot.medications.repository.MedicationLogRepository.class),
            Mockito.mock(com.forA.chatbot.medications.repository.MedicationBundleRepository.class),
            Mockito.mock(com.forA.chatbot.auth.repository.RefreshTokenRepository.class),
            Mockito.mock(com.forA.chatbot.auth.service.BlacklistService.class), chat,
            Mockito.mock(com.forA.chatbot.notification.repository.DeviceTokenRepository.class),
            Mockito.mock(com.forA.chatbot.diary.repository.DiaryReminderRepository.class),
            Mockito.mock(com.forA.chatbot.user.converter.UserProfileRequestConverter.class),
            Mockito.mock(com.forA.chatbot.user.converter.UserConverter.class));
    }
    @Bean ChatStepHandlerProvider steps(ChatSessionRepository sessions, ObjectMapper mapper) {
        return new ChatStepHandlerProvider(List.of(new GenderStepHandler(), Mockito.spy(new BirthYearStepHandler()), new JobStepHandler(),
            new EmotionStepHandler(), new ActionFeedbackStepHandler(), new ChatEndStepHandler(sessions),
            new DisorderStepHandler(), new SymptomStepHandler(),
            new SkillSelectStepHandler(mapper), new SkillConfirmStepHandler(Mockito.mock(ChatAiService.class),
                Mockito.mock(BehavioralSkillProvider.class))));
    }
}
