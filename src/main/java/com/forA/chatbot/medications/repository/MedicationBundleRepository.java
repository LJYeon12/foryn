package com.forA.chatbot.medications.repository;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.user.domain.User;
import jakarta.persistence.LockModeType;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MedicationBundleRepository extends JpaRepository<MedicationBundle, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT bundle FROM MedicationBundle bundle WHERE bundle.id = :id")
  Optional<MedicationBundle> findByIdForUpdate(@Param("id") Long id);

  List<MedicationBundle> findByUserId(Long userId);

  @Modifying
  @Transactional
  @Query("DELETE FROM MedicationBundle mb WHERE mb.user.id = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  /*해당 날짜의 복용 계획 조회*/
  List<MedicationBundle> findByUserIdAndDayOfWeekContainingAndIsDeletedFalse(Long userId,
      String dayOfWeek);

  // 메서드 추가
  @Query(
      "SELECT m FROM MedicationBundle m WHERE m.user.id = :userId AND m.isDeleted = false ORDER BY"
          + " m.createdAt DESC")
  List<MedicationBundle> findActiveByUserId(Long userId);

  @Query("SELECT m FROM MedicationBundle m WHERE m.id = :id AND m.isDeleted = false")
  Optional<MedicationBundle> findActiveById(Long id);

  // 사용자의 삭제되지 않은 복용 계획을 복용 시간 오름차순으로 조회
  List<MedicationBundle> findByUserAndIsDeletedFalseOrderByScheduledTimeAsc(User user);

  List<MedicationBundle> findByUserIdAndIsDeletedFalse(Long userId);

  //복용 계획 중복 체크
  boolean existsByUserAndBundleNameAndIsDeletedFalse(User user, String name);

  List<MedicationBundle> findAllByUserId(Long id);

  @Query("SELECT mb FROM MedicationBundle mb " +
      "JOIN FETCH mb.user u " +
      "WHERE mb.isDeleted = false " +
      "AND mb.dayOfWeek LIKE CONCAT('%', :dayOfWeek, '%') " +
      "AND mb.scheduledTime = :targetTime")
  List<MedicationBundle> findScheduledBundles(
      @Param("dayOfWeek") String dayOfWeek,
      @Param("targetTime") LocalTime targetTime
  );

  @Query("SELECT mb FROM MedicationBundle mb " +
      "JOIN FETCH mb.user u " +
      "WHERE mb.isDeleted = false " +
      "AND mb.dayOfWeek LIKE CONCAT('%', :dayOfWeek, '%')")
  List<MedicationBundle> findByDayOfWeekContainingAndIsDeletedFalse(
      @Param("dayOfWeek") String dayOfWeek
  );
}
