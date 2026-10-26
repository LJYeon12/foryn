package com.forA.chatbot.medications.repository;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationLog;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

  @Modifying
  @Transactional
  @Query("DELETE FROM MedicationLog ml WHERE ml.medicationBundle.user.id = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  Optional<MedicationLog> findByMedicationBundleAndDate(
      MedicationBundle medicationBundle, LocalDate date);

  // 특정 사용자의 특정 기간 동안의 모든 복용 기록을 조회
  @Query("SELECT ml FROM MedicationLog ml JOIN ml.medicationBundle mb WHERE mb.user = :user AND ml.date BETWEEN :startDate AND :endDate")
  List<MedicationLog> findByMedicationBundle_UserAndDateBetween(@Param("user") User user, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  /**
   * 특정 사용자의 특정 날짜에 복용 완료한 약물 로그 조회
   * - 감정 리포트에서 복약 시 기록된 컨디션(감정)을 찾기 위해 사용
   */
  @Query(
      "SELECT ml FROM MedicationLog ml "
          + "JOIN ml.medicationBundle mb "
          + "WHERE mb.user.id = :userId "
          + "AND ml.date = :date "
          + "AND ml.isTaken = :isTaken")
  List<MedicationLog> findByUserIdAndDateAndIsTaken(
      @Param("userId") Long userId, @Param("date") LocalDate date, @Param("isTaken") Boolean isTaken);

  @Query(
      "SELECT ml FROM MedicationLog ml "
          + "JOIN ml.medicationBundle mb "
          + "WHERE mb.user.id = :userId "
          + "AND ml.date BETWEEN :startDate AND :endDate "
          + "AND ml.isTaken = :isTaken "
          + "ORDER BY ml.date ASC, ml.id ASC")
  List<MedicationLog> findByUserIdAndDateBetweenAndIsTaken(
      @Param("userId") Long userId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("isTaken") Boolean isTaken);

  /**
   * 특정 기간 동안 특정 번들들의 복용 기록 개수 조회
   */
  @Query("SELECT COUNT(ml) FROM MedicationLog ml " +
      "WHERE ml.medicationBundle.id IN :bundleIds " +
      "AND ml.date BETWEEN :startDate AND :endDate " +
      "AND ml.isTaken = :isTaken")
  Long countByBundleIdsAndDateRangeAndIsTaken(
      @Param("bundleIds") List<Long> bundleIds,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("isTaken") Boolean isTaken
  );

  /**
   * 특정 날짜에 해당 유저가 복용해야 할 총 번들 개수 조회
   */
  @Query("SELECT COUNT(mb) FROM MedicationBundle mb " +
      "WHERE mb.user = :user " +
      "AND mb.isDeleted = false " +
      "AND mb.dayOfWeek LIKE CONCAT('%', :dayOfWeek, '%')")
  long countTotalBundlesToTake(@Param("user") User user, @Param("dayOfWeek") String dayOfWeek);

  /**
   * 특정 날짜에 해당 유저가 실제 복용 완료(isTaken=true)한 기록 개수 조회
   */
  @Query("SELECT COUNT(ml) FROM MedicationLog ml " +
      "JOIN ml.medicationBundle mb " +
      "WHERE mb.user = :user " +
      "AND ml.date = :date " +
      "AND ml.isTaken = true")
  long countCompletedLogs(@Param("user") User user, @Param("date") LocalDate date);

}
