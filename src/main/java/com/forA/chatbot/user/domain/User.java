package com.forA.chatbot.user.domain;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.exception.handler.UserHandler;
import com.forA.chatbot.enums.Gender;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.global.BaseTimeEntity;
import com.forA.chatbot.user.converter.DisorderSetConverter;
import com.forA.chatbot.user.converter.JobSetConverter;
import com.forA.chatbot.user.converter.SymptomSetConverter;
import com.forA.chatbot.user.domain.enums.DisorderType;
import com.forA.chatbot.user.domain.enums.JobType;
import com.forA.chatbot.user.domain.enums.SymptomType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor // Builder를 위한 전체 필드 생성자 추가
@Builder // Builder 패턴 사용
@Getter
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Getter
  private Long id;

  // --- Apple 로그인으로 추가될 필드 ---
  @Column(nullable = false, unique = true)
  private String email; // 이메일 필드 추가

  @Getter
  @Column(unique = true, name = "provider_user_id")
  private String providerUserId;

  @Column(nullable = false, name = "provider_type", columnDefinition = "VARCHAR(50)")
  @Enumerated(EnumType.STRING)
  private ProviderType providerType;

  // 논리 삭제 필드
  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  @Builder.Default
  private boolean isDeleted = false;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(nullable = false, columnDefinition = "VARCHAR(50) DEFAULT 'UNKNOWN'")
  @Builder.Default
  @Enumerated(EnumType.STRING) // 숫자가 아닌 스트링으로 DB에 저장되도록
  private Gender gender = Gender.UNKNOWN;

  @Column(name = "birth_year")
  private Integer birthYear;

  @Column(length = 100)
  private String nickname;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "time_zone", nullable = false, length = 64)
  @Builder.Default
  private String timeZone = "Asia/Seoul";

  @Convert(converter = JobSetConverter.class)
  @Column(columnDefinition = "JSON")
  @Size(max = 2, message = "최대 2개의 job만 선택할 수 있습니다")
  @Builder.Default
  private Set<JobType> jobs = new HashSet<>();

  @Convert(converter = DisorderSetConverter.class)
  @Column(columnDefinition = "JSON")
  @Size(max = 2, message = "최대 2개의 disorder만 선택할 수 있습니다")
  @Builder.Default
  private Set<DisorderType> disorders = new HashSet<>();

  @Convert(converter = SymptomSetConverter.class)
  @Column(columnDefinition = "JSON")
  @Size(max = 6, message = "최대 6개의 symptom만 선택할 수 있습니다")
  @Builder.Default
  private Set<SymptomType> symptoms = new HashSet<>();

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  /*
   * 성별 업데이트 (최초 1회만 가능)
   * 이미 설정된 경우 예외 발생
   */
  public void updateGender(Gender gender) {
    // 기존 값이 있고(UNKNOWN 아님), 입력된 값과 '다를 경우'에만 에러 발생
    if (this.gender != null && this.gender != Gender.UNKNOWN && !this.gender.equals(gender)) {
      throw new UserHandler(ErrorStatus.USER_GENDER_ALREADY_EXIST);
    }
    // 값이 같거나 초기 상태면 업데이트(덮어쓰기) 허용 -> 멱등성 보장
    this.gender = gender;
  }

  /** 생년 업데이트 (최초 1회만 가능) 이미 설정된 경우 예외 발생 */
  public void updateBirthYear(Integer birthYear) {
    // 이미 설정된 경우 수정 불가
    if (this.birthYear != null && !this.birthYear.equals(birthYear)) {
      throw new UserHandler(ErrorStatus.USER_BIRTH_YEAR_ALREADY_EXIST);
    }
    this.birthYear = birthYear;
  }

  public void updateJobs(Set<JobType> jobs) {
    this.jobs = jobs != null ? jobs : new HashSet<>();
  }

  public void updateDisorders(Set<DisorderType> disorders) {
    this.disorders = disorders != null ? disorders : new HashSet<>();
  }

  public void updateSymptoms(Set<SymptomType> symptoms) {
    this.symptoms = symptoms != null ? symptoms : new HashSet<>();
  }

  public void resetUserData() {
    this.nickname = null;
    this.birthYear = null;
    this.gender = Gender.UNKNOWN;
    this.jobs = new HashSet<>();
    this.disorders = new HashSet<>();
    this.symptoms = new HashSet<>();
  }

  // 회원 탈퇴 (논리 삭제 및 복구 전략)
  public void deactivate() {
    this.isDeleted = true;
    this.deletedAt = LocalDateTime.now();
  }

  // 계정 복구
  public void activate() {
    this.isDeleted = false;
    this.deletedAt = null;
  }
}
