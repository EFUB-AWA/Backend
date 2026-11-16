package com.irion.irion_backend.domain.costbenchmark.service;

import com.irion.irion_backend.domain.costbenchmark.client.KosisApiClient;
import com.irion.irion_backend.domain.costbenchmark.dto.KosisDataRow;
import com.irion.irion_backend.domain.costbenchmark.entity.CostBenchmark;
import com.irion.irion_backend.domain.costbenchmark.entity.CostBenchmarkSource;
import com.irion.irion_backend.domain.costbenchmark.repository.CostBenchmarkRepository;
import com.irion.irion_backend.domain.costbenchmark.repository.CostBenchmarkSourceRepository;
import com.irion.irion_backend.domain.externaldata.entity.ExternalDataSnapshot;
import lombok.RequiredArgsConstructor;
import com.irion.irion_backend.domain.externaldata.repository.ExternalDataSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CostBenchmarkImportService {

    private static final int YEAR = 2023;
    private static final String SEOUL_CODE = "11";
    private static final BigDecimal MILLION =
            new BigDecimal("1000000");
    private static final BigDecimal MONTHS =
            new BigDecimal("12");

    private final KosisApiClient kosisApiClient;
    private final KosisOperatingCostParser parser;
    private final CostBenchmarkRepository benchmarkRepository;
    private final ExternalDataSnapshotRepository snapshotRepository;
    private final CostBenchmarkSourceRepository sourceRepository;

    @Transactional
    public int importBenchmarks() {

        // 1. KOSIS API 호출
        String costJson = kosisApiClient.fetchOperatingCosts();
        String salesJson = kosisApiClient.fetchSalesStatistics();

        // 2. JSON 파싱
        List<KosisDataRow> costRows = parser.parseRows(costJson);
        List<KosisDataRow> salesRows = parser.parseRows(salesJson);

        // 3. 원본 데이터 저장
        ExternalDataSnapshot costSnapshot = snapshotRepository.save(
                ExternalDataSnapshot.create(
                        "KOSIS",
                        "DT_3ME0144",
                        YEAR,
                        costJson
                )
        );

        ExternalDataSnapshot salesSnapshot = snapshotRepository.save(
                ExternalDataSnapshot.create(
                        "KOSIS",
                        "DT_3ME0142",
                        YEAR,
                        salesJson
                )
        );

        // 4. 서울시 업종 코드 추출
        Map<String, String> industries = costRows.stream()
                .filter(row -> SEOUL_CODE.equals(row.regionCode()))
                .filter(row -> row.industryCode() != null)
                .filter(row -> row.industryName() != null)
                .collect(java.util.stream.Collectors.toMap(
                        KosisDataRow::industryCode,
                        KosisDataRow::industryName,
                        (first, second) -> first
                ));

        int importedCount = 0;

        // 5. 업종별 비용 계산 및 저장
        for (Map.Entry<String, String> entry : industries.entrySet()) {

            String industryCode = entry.getKey();
            String industryName = entry.getValue();

            Map<String, BigDecimal> costs =
                    parser.parseOperatingCosts(
                            costRows,
                            SEOUL_CODE,
                            industryCode
                    );

            Map<String, BigDecimal> sales =
                    parser.parseItems(
                            salesRows,
                            SEOUL_CODE,
                            industryCode,
                            Set.of("T001")
                    );

            if (!costs.keySet().containsAll(
                    Set.of("T03", "T04", "T05", "T06"))
                    || !sales.containsKey("T001")) {
                continue;
            }

            BigDecimal annualLabor = costs.get("T03");
            BigDecimal annualRent = costs.get("T04");
            BigDecimal annualOther = costs.get("T05");
            BigDecimal annualCostOfSales = costs.get("T06");
            BigDecimal annualSales = sales.get("T001");

            if (annualSales.signum() <= 0) {
                continue;
            }

            // 월평균 인건비 (원)
            Long monthlyLaborCost = annualLabor
                    .multiply(MILLION)
                    .divide(MONTHS, 0, RoundingMode.HALF_UP)
                    .longValueExact();

            // 월평균 기타 운영비 (원)
            Long monthlyOtherOperatingCost = annualRent
                    .add(annualOther)
                    .multiply(MILLION)
                    .divide(MONTHS, 0, RoundingMode.HALF_UP)
                    .longValueExact();

            // 매출원가율 (%)
            BigDecimal costOfSalesRate = annualCostOfSales
                    .divide(annualSales, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);

            // 6. 기존 데이터 조회 또는 생성
            CostBenchmark benchmark = benchmarkRepository
                    .findByKosisIndustryCodeAndRegionCodeAndReferenceYear(
                            industryCode,
                            SEOUL_CODE,
                            YEAR
                    )
                    .orElse(null);

            if (benchmark == null) {
                benchmark = CostBenchmark.create(
                        industryCode,
                        industryName,
                        SEOUL_CODE,
                        YEAR,
                        monthlyLaborCost,
                        costOfSalesRate,
                        monthlyOtherOperatingCost
                );
            } else {
                benchmark.updateCosts(
                        industryName,
                        monthlyLaborCost,
                        costOfSalesRate,
                        monthlyOtherOperatingCost
                );
            }

            benchmark = benchmarkRepository.save(benchmark);

            // 7. 기존 통계 출처 연결 삭제
            sourceRepository.deleteByCostBenchmarkId(benchmark.getId());

            // 8. 최신 통계 출처 연결
            sourceRepository.save(
                    CostBenchmarkSource.create(
                            benchmark,
                            costSnapshot
                    )
            );

            sourceRepository.save(
                    CostBenchmarkSource.create(
                            benchmark,
                            salesSnapshot
                    )
            );
            importedCount++;
        }

        return importedCount;
    }
}
