package com.forA.chatbot.report.fault;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 실제 드라이버와 응답 한 번의 지연으로 JDBC isValid의 초 단위 반올림 문제를 드러낸다. */
class ReportValidationDeadlineFaultTest {
  @Test
  @Timeout(30)
  void delayedValidationCannotHoldAnEntryForSixHundredMilliseconds() throws Exception {
    String jdbc = ReportFaultSupport.requireJdbc(System.getenv("REPORT_FAULT_JDBC_URL"));
    int databasePort = URI.create(jdbc.substring(5)).getPort();
    try (DelayedResponse proxy = new DelayedResponse(databasePort)) {
      String redirected = jdbc.replace(":" + databasePort + "/", ":" + proxy.port() + "/");
      var settings = ReportFaultConfiguration.poolSettings(redirected, new SimpleMeterRegistry());
      settings.setMaximumPoolSize(1);
      settings.setMinimumIdle(1);
      try (var pool = new HikariDataSource(settings)) {
        try (var warm = pool.getConnection()) {
          assertThat(warm.isValid(1)).isTrue();
        }
        TimeUnit.MILLISECONDS.sleep(700); // Hikari의 연결 검증을 생략하는 500ms 구간이 지나야 한다.
        proxy.delayNext.set(true);
        long start = System.nanoTime();
        try (var connection = pool.getConnection()) {
          assertThat(connection.isClosed()).isFalse();
        } catch (SQLException expected) {
          // 연결 획득 제한 시간 안에 느린 연결을 거부하는 것도 유효하다.
        }
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        System.out.println("REPORT_VALIDATION_DEADLINE elapsedMs=" + elapsedMs);
        assertThat(proxy.delayed.get()).as("The real validation response was delayed").isTrue();
        assertThat(elapsedMs).as("250ms validation plus acquisition/host scheduling margin").isLessThan(500);
      }
    }
  }

  private static final class DelayedResponse implements AutoCloseable {
    final AtomicBoolean delayNext = new AtomicBoolean();
    final AtomicBoolean delayed = new AtomicBoolean();
    final ServerSocket listener = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
    final ExecutorService workers = Executors.newCachedThreadPool();
    final List<Socket> sockets = new CopyOnWriteArrayList<>();

    DelayedResponse(int targetPort) throws IOException {
      workers.submit(() -> {
        while (!listener.isClosed()) {
          try {
            Socket client = listener.accept();
            Socket server = new Socket("127.0.0.1", targetPort);
            sockets.add(client);
            sockets.add(server);
            workers.submit(() -> copy(client, server, false));
            workers.submit(() -> copy(server, client, true));
          } catch (IOException stopped) {
            break;
          }
        }
      });
    }

    int port() { return listener.getLocalPort(); }

    private void copy(Socket source, Socket target, boolean response) {
      try {
        byte[] bytes = new byte[8192];
        int count;
        while ((count = source.getInputStream().read(bytes)) >= 0) {
          if (response && delayNext.compareAndSet(true, false)) {
            delayed.set(true);
            TimeUnit.MILLISECONDS.sleep(600);
          }
          target.getOutputStream().write(bytes, 0, count);
          target.getOutputStream().flush();
        }
      } catch (IOException stopped) {
        // 검증 시간이 초과되면 지연 응답을 기다리는 중에 소켓을 닫는다.
      } catch (InterruptedException stopped) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void close() throws Exception {
      listener.close();
      for (Socket socket : sockets) {
        socket.close();
      }
      workers.shutdownNow();
    }
  }
}
