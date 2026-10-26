package com.forA.chatbot.user.domain;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.user.domain.enums.SymptomType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class UserTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("성별 최초 업데이트 성공")
  void updateGender_Success_FirstTime() {
    // 준비
    User user = User.builder()
        .gender(Gender.UNKNOWN) // 초기 상태
        .build();

    // 실행
    user.updateGender(Gender.FEMALE);

    // 검증
    assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
  }

  @Test
  @DisplayName("멱등성 테스트: 이미 설정된 성별과 '동일한' 성별로 요청 시 성공해야 함")
  void updateGender_Success_SameValue() {
    // 준비
    User user = User.builder()
        .gender(Gender.FEMALE)
        .build();

    // 실행
    user.updateGender(Gender.FEMALE);

    // 검증
    // 에러 없이 통과하고 값은 유지되어야 함
    assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
  }

  @Test
  @DisplayName("실패 테스트: 이미 설정된 성별과 '다른' 성별로 요청 시 예외 발생")
  void updateGender_Fail_DifferentValue() {
    // 준비
    User user = User.builder()
        .gender(Gender.FEMALE)
        .build();

    // 실행 및 검증
    // MALE로 변경 시도 -> 예외 발생해야 함
    assertThatThrownBy(() -> user.updateGender(Gender.MALE))
        .isInstanceOf(UserHandler.class)
        .hasFieldOrPropertyWithValue("code", ErrorStatus.USER_GENDER_ALREADY_EXIST);
  }

  @Test
  @DisplayName("회원 탈퇴(Soft Delete) 시 isDeleted=true, deletedAt 설정 확인 (개인정보 유지)")
  void deactivate() {
    // 준비
    User user = User.builder()
        .email("test@example.com")
        .providerUserId("apple_1234")
        .build();

    // 실행
    user.deactivate();

    // 검증
    assertThat(user.isDeleted()).isTrue();
    assertThat(user.getDeletedAt()).isNotNull();

    // 데이터 보존 확인 (마스킹 없음)
    assertThat(user.getEmail()).isEqualTo("test@example.com");
    assertThat(user.getProviderUserId()).isEqualTo("apple_1234");
  }

  @Test
  @DisplayName("계정 복구 시 isDeleted=false, deletedAt=null 초기화 확인")
  void activate() {
    // 준비
    User user = User.builder()
        .email("test@example.com")
        .build();
    user.deactivate(); // 먼저 탈퇴 처리

    // 실행
    user.activate();

    // 검증
    assertThat(user.isDeleted()).isFalse();
    assertThat(user.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("사용자 데이터 초기화 시 신원 정보(fullName, firstName, lastName)는 유지되고 프로필 정보는 초기화되어야 한다")
  void resetUserData_ShouldPreserveIdentity_And_ResetProfile() {
    // 준비
    User user = User.builder()
        .email("test@apple.com")
        .providerUserId("apple_unique_id")
        .firstName("Test")
        .lastName("User")
        .fullName("Test User")
        .nickname("Old Nickname")
        .birthYear(1990)
        .gender(Gender.MALE)
        // job, disorder, symptom 등은 Set이라 초기화 필요
        .build();

    // 실행
    user.resetUserData();

    // 검증
    // 1. 신원 정보 유지 확인
    assertThat(user.getEmail()).isEqualTo("test@apple.com");
    assertThat(user.getProviderUserId()).isEqualTo("apple_unique_id");
    assertThat(user.getFirstName()).isEqualTo("Test");
    assertThat(user.getLastName()).isEqualTo("User");
    assertThat(user.getFullName()).isEqualTo("Test User");

    // 2. 프로필 정보 초기화 확인
    assertThat(user.getNickname()).isNull();
    assertThat(user.getBirthYear()).isNull();
    assertThat(user.getGender()).isEqualTo(Gender.UNKNOWN);
    assertThat(user.getJobs().isEmpty()).isTrue();
    assertThat(user.getDisorders().isEmpty()).isTrue();
    assertThat(user.getSymptoms().isEmpty()).isTrue();
  }

  @Test
  @DisplayName("멱등성 테스트: 이미 설정된 생년과 동일한 값으로 요청 시 성공해야 함")
  void updateBirthYear_Success_SameValue() {
    // 준비
    User user = User.builder()
        .birthYear(1990)
        .build();

    // 실행
    user.updateBirthYear(1990);

    // 검증
    assertThat(user.getBirthYear()).isEqualTo(1990);
  }

  @Test
  @DisplayName("실패 테스트: 이미 설정된 생년과 다른 값으로 요청 시 예외 발생")
  void updateBirthYear_Fail_DifferentValue() {
    // 준비
    User user = User.builder()
        .birthYear(1990)
        .build();

    // 실행 및 검증
    assertThatThrownBy(() -> user.updateBirthYear(2000))
        .isInstanceOf(UserHandler.class)
        .hasFieldOrPropertyWithValue("code", ErrorStatus.USER_BIRTH_YEAR_ALREADY_EXIST);
  }

  @Test
  @DisplayName("symptoms는 최대 6개까지 저장 가능하다")
  void symptoms_allowsUpToSixSelections() {
    User user =
        User.builder()
            .email("test@example.com")
            .providerUserId("apple_1234")
            .fullName("Test User")
            .symptoms(
                java.util.Set.of(
                    SymptomType.NO_INTEREST_IN_ENJOYABLE_ACTIVITIES,
                    SymptomType.LETHARGY_AND_UNWILLINGNESS,
                    SymptomType.FEELING_WORTHLESS,
                    SymptomType.CONSTANT_FUTURE_WORRIES,
                    SymptomType.FEAR_OF_MAKING_MISTAKES,
                    SymptomType.CHEST_TIGHTNESS_AND_BREATHLESSNESS))
            .build();

    assertThat(validator.validate(user).isEmpty()).isTrue();
  }
}
