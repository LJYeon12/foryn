package com.forA.chatbot.chat.fault;

import static org.assertj.core.api.Assertions.assertThat;

import com.forA.chatbot.config.ChatTransactionConfiguration;
import com.forA.chatbot.config.migration.V15__Prepare_chat_recovery;
import com.forA.chatbot.user.domain.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** 수동 구성한 장애 실험 문맥에 더해 Boot의 실제 Flyway/JPA/Mongo 빈 초기화 순서를 사용한다. */
class ChatDatabaseBootstrapFaultTest {
    @Test void productionManagersAndJavaMigrationAreSelectedByBootAutoConfiguration() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class,
                MongoAutoConfiguration.class, MongoDataAutoConfiguration.class, FlywayAutoConfiguration.class))
            .withUserConfiguration(EntityPackages.class, ChatTransactionConfiguration.class, V15__Prepare_chat_recovery.class)
            .withPropertyValues("spring.datasource.url=" + ChatFaultConfiguration.requireJdbc(System.getenv("CHAT_FAULT_JDBC_URL")),
                "spring.datasource.username=root", "spring.datasource.password=",
                "spring.data.mongodb.uri=" + ChatFaultConfiguration.requireMongo(System.getenv("CHAT_FAULT_MONGO_URI")),
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.connection.handling_mode=DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(PlatformTransactionManager.class)).isInstanceOf(JpaTransactionManager.class);
                assertThat(context.getBean("mongoTransactionManager")).isInstanceOf(MongoTransactionManager.class);
                assertThat(context.getBean(V15__Prepare_chat_recovery.class)).isNotNull();
                assertThat(context.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("15");
                assertThat(context.getBean(MongoTemplate.class).collectionExists("chat_write_guards")).isTrue();
            });
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = User.class)
    @EntityScan(basePackageClasses = User.class)
    static class EntityPackages { }
}
