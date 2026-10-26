package com.forA.chatbot.user.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCleanupSchedulerTest {

    @InjectMocks
    private UserCleanupScheduler userCleanupScheduler;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("30일 지난 탈퇴 회원은 영구 삭제되어야 한다")
    void deleteExpiredSoftDeletedUsers() {
        // 준비
        User expiredUser = User.builder().id(1L).build();
        expiredUser.deactivate();
        // deletedAt을 31일 전으로 설정해야 하는데, deactivate()는 현재 시간으로 설정함.
        // 테스트용으로 리플렉션이나 빌더로 설정 필요하지만, 여기서는 Repository가 반환하는 User가 "30일 지난 유저"라고 가정.

        given(userRepository.findExpiredSoftDeletedUsers(any(LocalDateTime.class)))
                .willReturn(List.of(expiredUser));

        // 실행
        userCleanupScheduler.cleanupExpiredUsers();

        // 검증
        verify(userService, times(1)).deleteUserRelatedData(1L);
        verify(userRepository, times(1)).delete(expiredUser);
    }
}
