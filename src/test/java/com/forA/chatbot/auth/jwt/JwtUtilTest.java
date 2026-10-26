package com.forA.chatbot.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.forA.chatbot.apiPayload.exception.GeneralException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

  private static final String SECRET = "test-secret-key-for-jjwt-parser-reuse-1234567890";
  private static final long ONE_HOUR_MILLIS = 3_600_000L;

  @Test
  @DisplayName("creates and validates access token")
  void createAndValidateAccessToken() {
    JwtUtil jwtUtil = new JwtUtil(SECRET, ONE_HOUR_MILLIS);

    String token = jwtUtil.createAccessToken("31");

    assertThat(jwtUtil.validateToken(token)).isTrue();
    assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(31L);
    assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    assertThat(jwtUtil.getIssuedAtFromToken(token)).isNotNull();
    assertThat(jwtUtil.getExpirationFromToken(token)).isAfter(LocalDateTime.now());
  }

  @Test
  @DisplayName("returns false for invalid token")
  void invalidTokenReturnsFalse() {
    JwtUtil jwtUtil = new JwtUtil(SECRET, ONE_HOUR_MILLIS);

    assertThat(jwtUtil.validateToken("invalid-token")).isFalse();
    assertThat(jwtUtil.isTokenExpired("invalid-token")).isTrue();
  }

  @Test
  @DisplayName("expired token is rejected")
  void expiredTokenIsRejected() {
    JwtUtil jwtUtil = new JwtUtil(SECRET, -1_000L);
    String token = jwtUtil.createAccessToken("31");

    assertThat(jwtUtil.validateToken(token)).isFalse();
    assertThat(jwtUtil.isTokenExpired(token)).isTrue();
    assertThatCode(() -> jwtUtil.getUserIdFromToken(token)).isInstanceOf(GeneralException.class);
  }

  @Test
  @DisplayName("reused parser validates tokens concurrently")
  void validatesTokensConcurrently() throws Exception {
    JwtUtil jwtUtil = new JwtUtil(SECRET, ONE_HOUR_MILLIS);
    String token = jwtUtil.createAccessToken("31");
    ExecutorService executorService = Executors.newFixedThreadPool(8);
    List<Callable<Boolean>> tasks = new ArrayList<>();

    for (int i = 0; i < 100; i++) {
      tasks.add(
          () -> jwtUtil.validateToken(token) && jwtUtil.getUserIdFromToken(token).equals(31L));
    }

    try {
      List<Future<Boolean>> results = executorService.invokeAll(tasks);

      for (Future<Boolean> result : results) {
        assertThat(result.get()).isTrue();
      }
    } finally {
      executorService.shutdownNow();
      assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }
}
