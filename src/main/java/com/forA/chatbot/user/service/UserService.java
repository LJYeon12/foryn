package com.forA.chatbot.user.service;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.auth.repository.RefreshTokenRepository;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.auth.service.BlacklistService;
import com.forA.chatbot.chat.service.ChatService;
import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.diary.repository.DiaryReminderRepository;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.converter.UserConverter;
import com.forA.chatbot.user.converter.UserProfileRequestConverter;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import com.forA.chatbot.user.dto.NicknameResponse;
import com.forA.chatbot.user.dto.UserDeleteResponse;
import com.forA.chatbot.user.dto.UserProfileResponse;
import com.forA.chatbot.user.dto.UserProfileUpdateRequest;
import com.forA.chatbot.user.dto.UserResetResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;
  private final MedicationLogRepository medicationLogRepository;
  private final MedicationBundleRepository medicationBundleRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final BlacklistService blacklistService;
  private final ChatService chatService;
  private final DeviceTokenRepository deviceTokenRepository;
  private final DiaryReminderRepository diaryReminderRepository;
  private final UserProfileRequestConverter userProfileRequestConverter;
  private final UserConverter userConverter;
  @PersistenceContext private EntityManager entityManager;

  @Transactional
  public NicknameResponse updateNickname(Long userId, String nickname) {
    User user = userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

    user.updateNickname(nickname);
    userRepository.save(user);

    return new NicknameResponse(nickname);
  }

  @Transactional
  public UserProfileResponse updateUserProfile(Long userId, UserProfileUpdateRequest request) {
    User user = userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

    if (request.getNickname() != null) {
      user.updateNickname(request.getNickname());
    }

    if (request.getJobs() != null) {
      Set<JobType> jobs = userProfileRequestConverter.convertJobs(request.getJobs());
      user.updateJobs(jobs);
    }

    if (request.getDisorders() != null) {
      Set<DisorderType> disorders = userProfileRequestConverter.convertDisorders(request.getDisorders());
      user.updateDisorders(disorders);
    }

    if (request.getSymptoms() != null) {
      Set<SymptomType> symptoms = userProfileRequestConverter.convertSymptoms(request.getSymptoms());
      user.updateSymptoms(symptoms);
    }

    userRepository.save(user);

    // PATCH 응답도 실제 저장된 알림 설정을 반영해 클라이언트가 빈 목록으로 오해하지 않게 한다.
    List<DiaryReminder> diaryReminders = diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(userId);

    return userConverter.toUserProfileResponse(user, diaryReminders);
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getUserProfile(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    // 사용자 프로필 응답에는 현재 한줄일기 알림 설정을 함께 내려준다. 별도 조회 API는 만들지 않는다.
    List<DiaryReminder> diaryReminders = diaryReminderRepository.findByUserIdOrderByReminderTimeAsc(userId);

    return userConverter.toUserProfileResponse(user, diaryReminders);
  }

  @Transactional
  public UserResetResponse resetUserData(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

    chatService.deleteAllChatDataByUserId(userId);
    // 최초 조회가 마지막 채팅 커밋보다 앞설 수 있다. 최신 관리 상태를 다시 불러온다.
    entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
    medicationLogRepository.deleteByUserId(userId);
    medicationBundleRepository.deleteByUserId(userId);

    user.resetUserData();
    userRepository.save(user);
    log.info("User data reset completed: userId={}", userId);
    return UserResetResponse.success();
  }

  @Transactional
  public UserDeleteResponse deactivateUser(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

    user.deactivate();
    refreshTokenRepository.deleteByUserId(userId);
    deviceTokenRepository.deleteAllByUserId(userId);
    userRepository.save(user);

    log.info("User soft delete completed: userId={}", userId);
    return UserDeleteResponse.success();
  }

  @Transactional
  public void deleteUserRelatedData(Long userId) {
    log.info("Delete user related data started: userId={}", userId);

    try {
      refreshTokenRepository.deleteByUserId(userId);
      log.info("Refresh tokens deleted: userId={}", userId);

      blacklistService.removeUserTokens(userId);
      log.info("Blacklisted tokens deleted: userId={}", userId);

      medicationLogRepository.deleteByUserId(userId);
      log.info("Medication logs deleted: userId={}", userId);

      medicationBundleRepository.deleteByUserId(userId);
      log.info("Medication bundles deleted: userId={}", userId);

      chatService.deleteAllChatDataByUserId(userId);
      log.info("Chat data deleted: userId={}", userId);

      log.info("Delete user related data completed: userId={}", userId);
    } catch (Exception e) {
      log.error("Delete user related data failed: userId={}", userId, e);
      throw new UserHandler(ErrorStatus.USER_DELETE_FAILED);
    }
  }
}
