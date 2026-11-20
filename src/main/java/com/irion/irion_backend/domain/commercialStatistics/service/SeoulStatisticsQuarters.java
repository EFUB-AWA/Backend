package com.irion.irion_backend.domain.commercialStatistics.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Predicate;

final class SeoulStatisticsQuarters {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_CANDIDATE_QUARTERS = 8;

    private SeoulStatisticsQuarters() {}

    static Optional<String> findLatestPublishedQuarter(LocalDate today, Predicate<String> isPublished) {
        YearMonth quarterStart = YearMonth.from(today).minusMonths((today.getMonthValue() - 1) % 3);

        for (int i = 0; i < MAX_CANDIDATE_QUARTERS; i++) {
            YearMonth candidate = quarterStart.minusMonths(3L * i);
            int quarter = (candidate.getMonthValue() - 1) / 3 + 1;
            String quarterCode = Integer.toString(candidate.getYear()) + quarter;
            if (isPublished.test(quarterCode)) {
                return Optional.of(quarterCode);
            }
        }

        return Optional.empty();
    }
}
