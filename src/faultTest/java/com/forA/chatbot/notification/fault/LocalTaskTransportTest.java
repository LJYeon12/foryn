package com.forA.chatbot.notification.fault;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import org.junit.jupiter.api.Test;

class LocalTaskTransportTest {
  @Test
  void refusesNonLoopbackEndpointsBeforeAnyNetworkCall() {
    assertThatIllegalArgumentException().isThrownBy(() ->
        LocalTaskTransport.requireLoopback(URI.create("https://example.com")));
    assertThatIllegalArgumentException().isThrownBy(() ->
        LocalTaskTransport.requireLoopback(URI.create("http://127.0.0.1.evil.example:8080")));
  }

  @Test
  void refusesOtherDatabasesAndJdbcOptions() {
    assertThatIllegalArgumentException().isThrownBy(() ->
        LocalTaskTransport.requireDisposableDatabase("jdbc:mysql://127.0.0.1:3306/production"));
    assertThatIllegalArgumentException().isThrownBy(() ->
        LocalTaskTransport.requireDisposableDatabase("jdbc:mysql://remote:3306/outbox_fault_check"));
    assertThatIllegalArgumentException().isThrownBy(() ->
        LocalTaskTransport.requireDisposableDatabase("jdbc:mysql://127.0.0.1:3306/outbox_fault_check"
            + "?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&socketFactory=unexpected"));
  }
}
