
package com.irion.irion_backend.domain.costbenchmark.service;

import com.irion.irion_backend.domain.costbenchmark.dto.CostBenchmarkResponse;
import com.irion.irion_backend.domain.costbenchmark.entity.CostBenchmark;
import com.irion.irion_backend.domain.costbenchmark.entity.IndustryCostBenchmarkMapping;
import com.irion.irion_backend.domain.costbenchmark.exception.CostBenchmarkErrorCode;
import com.irion.irion_backend.domain.costbenchmark.repository.CostBenchmarkRepository;
import com.irion.irion_backend.domain.costbenchmark.repository.CostBenchmarkSourceRepository;
import com.irion.irion_backend.domain.costbenchmark.repository.IndustryCostBenchmarkMappingRepository;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostBenchmarkService {

    private static final String SEOUL_CODE = "11";
    private static final String OVERALL_INDUSTRY_CODE = "0";
    private static final int REFERENCE_YEAR = 2023;

    private final IndustryRepository industryRepository;
    private final IndustryCostBenchmarkMappingRepository mappingRepository;
    private final CostBenchmarkRepository benchmarkRepository;
    private final CostBenchmarkSourceRepository sourceRepository;

    public CostBenchmarkResponse getCostBenchmark(Long industryId) {

        // 1. 이리ON 업종 존재 여부 확인
        if (!industryRepository.existsById(industryId)) {
            throw new BusinessException(
                    CostBenchmarkErrorCode.INDUSTRY_NOT_FOUND
            );
        }

        // 2. 이리ON 업종과 KOSIS 업종의 매핑 조회
        IndustryCostBenchmarkMapping mapping = mappingRepository
                .findByIndustryId(industryId)
                .orElse(null);

        CostBenchmark benchmark;
        String benchmarkType;

        if (mapping != null) {
            // 매핑된 업종의 평균 사용
            benchmark = mapping.getCostBenchmark();
            benchmarkType = "INDUSTRY_AVERAGE";

        } else {
            // 매핑되지 않은 업종은 서울시 전산업 평균 사용
            benchmark = benchmarkRepository
                    .findByKosisIndustryCodeAndRegionCodeAndReferenceYear(
                            OVERALL_INDUSTRY_CODE,
                            SEOUL_CODE,
                            REFERENCE_YEAR
                    )
                    .orElseThrow(() -> new BusinessException(
                            CostBenchmarkErrorCode.COST_BENCHMARK_NOT_FOUND
                    ));

            benchmarkType = "OVERALL_AVERAGE";
        }

        // 3. 원본 통계 스냅샷 ID 조회
        List<Long> snapshotIds = sourceRepository
                .findByCostBenchmarkId(benchmark.getId())
                .stream()
                .map(source -> source.getExternalDataSnapshot().getId())
                .toList();

        // 4. 응답 반환
        return CostBenchmarkResponse.of(
                industryId,
                benchmark,
                benchmarkType,
                snapshotIds

        );
    }
}
