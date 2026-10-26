package com.forA.chatbot.diary.repository;

import com.forA.chatbot.diary.domain.DiaryReportSummaryCache;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiaryReportSummaryCacheRepository
    extends JpaRepository<DiaryReportSummaryCache, Long> {

  Optional<DiaryReportSummaryCache> findByUserIdAndSummaryDate(Long userId, LocalDate summaryDate);
}
