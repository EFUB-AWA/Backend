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
@Component
@RequiredArgsConstructor
public class ComparisonConsultationCleanupScheduler {

    private final ComparisonConsultationRepository comparisonConsultationRepository;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredConsultations() {

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        int deleted = comparisonConsultationRepository.deleteExpired(now);
        log.info("만료된 비교 컨설팅 삭제: {}건", deleted);
    }

}
