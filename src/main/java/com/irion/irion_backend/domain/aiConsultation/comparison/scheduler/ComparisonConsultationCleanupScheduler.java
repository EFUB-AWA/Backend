package com.irion.irion_backend.domain.aiConsultation.comparison.scheduler;

import com.irion.irion_backend.domain.aiConsultation.comparison.repository.ComparisonConsultationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "comparison.cleanup.enabled", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class ComparisonConsultationCleanupScheduler {

    private final ComparisonConsultationRepository comparisonConsultationRepository;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredConsultations() {

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        cleanupAt(now);
    }

    void cleanupAt(LocalDateTime now) {
        comparisonConsultationRepository.recoverPending(now.minusHours(1), now, now.plusHours(24));
        int deleted = comparisonConsultationRepository.deleteExpired(now);
        log.info("만료된 비교 컨설팅 삭제: {}건", deleted);
    }

}
