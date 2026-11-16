
package com.irion.irion_backend.domain.costbenchmark.service;

import com.irion.irion_backend.domain.costbenchmark.entity.CostBenchmark;
import com.irion.irion_backend.domain.costbenchmark.entity.IndustryCostBenchmarkMapping;
import com.irion.irion_backend.domain.costbenchmark.repository.CostBenchmarkRepository;
import com.irion.irion_backend.domain.costbenchmark.repository.IndustryCostBenchmarkMappingRepository;
import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CostBenchmarkMappingService {

    private static final String SEOUL_CODE = "11";
    private static final int REFERENCE_YEAR = 2023;

    // 서비스업 중 KOSIS 대분류가 명확한 업종
    private static final Map<String, String> SERVICE_MAPPING = Map.ofEntries(
            // 교육 서비스업
            Map.entry("CS200001", "P"),
            Map.entry("CS200002", "P"),
            Map.entry("CS200003", "P"),
            Map.entry("CS200004", "P"),
            Map.entry("CS200005", "P"),
            Map.entry("CS200038", "P"),

            // 전문과학기술서비스업
            Map.entry("CS200010", "M"),
            Map.entry("CS200011", "M"),
            Map.entry("CS200012", "M"),
            Map.entry("CS200013", "M"),
            Map.entry("CS200014", "M"),
            Map.entry("CS200015", "M"),
            Map.entry("CS200041", "M"),
            Map.entry("CS200042", "M"),

            // 예술 스포츠 및 여가관련 서비스업
            Map.entry("CS200016", "R"),
            Map.entry("CS200017", "R"),
            Map.entry("CS200018", "R"),
            Map.entry("CS200020", "R"),
            Map.entry("CS200021", "R"),
            Map.entry("CS200024", "R"),

            // 수리 및 기타 개인서비스업
            Map.entry("CS200023", "S"),
            Map.entry("CS200025", "S"),
            Map.entry("CS200026", "S"),
            Map.entry("CS200027", "S"),
            Map.entry("CS200028", "S"),
            Map.entry("CS200029", "S"),
            Map.entry("CS200030", "S"),
            Map.entry("CS200031", "S"),
            Map.entry("CS200032", "S"),

            // 부동산업
            Map.entry("CS200033", "L"),

            // 숙박 및 음식점업
            Map.entry("CS200034", "I"),
            Map.entry("CS200035", "I"),

            // 사업지원 및 임대 서비스업
            Map.entry("CS200043", "N"),
            Map.entry("CS200044", "N"),
            Map.entry("CS200045", "N"),
            Map.entry("CS200046", "N"),
            Map.entry("CS200047", "N")
    );

    // 대분류 연결이 애매하거나 KOSIS 통계가 없는 업종
    private static final Set<String> OVERALL_AVERAGE_INDUSTRIES = Set.of(
            "CS200006", // 일반의원
            "CS200007", // 치과의원
            "CS200008", // 한의원
            "CS200009", // 동물병원
            "CS200019", // PC방
            "CS200022", // 복권방
            "CS200036", // 고시원
            "CS200037", // 노래방
            "CS200039", // DVD방
            "CS200040"  // 녹음실
    );

    private final IndustryRepository industryRepository;
    private final CostBenchmarkRepository benchmarkRepository;
    private final IndustryCostBenchmarkMappingRepository mappingRepository;

    @Transactional
    public int initializeMappings() {

        int count = 0;

        for (Industry industry : industryRepository.findAll()) {

            if (mappingRepository.existsByIndustryId(industry.getId())) {
                continue;
            }

            String kosisCode = resolveKosisCode(industry.getCode());

            // 전체 평균을 사용할 업종은 매핑하지 않음
            if (kosisCode == null) {
                continue;
            }

            CostBenchmark benchmark = benchmarkRepository
                    .findByKosisIndustryCodeAndRegionCodeAndReferenceYear(
                            kosisCode,
                            SEOUL_CODE,
                            REFERENCE_YEAR
                    )
                    .orElse(null);

            // 통계가 없는 경우에도 전체 평균 사용
            if (benchmark == null) {
                continue;
            }

            mappingRepository.save(
                    IndustryCostBenchmarkMapping.create(
                            industry,
                            benchmark
                    )
            );

            count++;
        }

        return count;
    }

    private String resolveKosisCode(String industryCode) {

        if (industryCode == null) {
            return null;
        }

        // 외식업 전체
        if (industryCode.startsWith("CS100")) {
            return "I";
        }

        // 소매업 전체
        if (industryCode.startsWith("CS300")) {
            return "G";
        }

        // 전산업 평균 대상
        if (OVERALL_AVERAGE_INDUSTRIES.contains(industryCode)) {
            return null;
        }

        // 서비스업 개별 매핑
        return SERVICE_MAPPING.get(industryCode);
    }
}
