
package com.irion.irion_backend.domain.costbenchmark.config;

import com.irion.irion_backend.domain.costbenchmark.service.CostBenchmarkImportService;
import com.irion.irion_backend.domain.costbenchmark.service.CostBenchmarkMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("cost-import")
@RequiredArgsConstructor
public class CostBenchmarkImportRunner implements ApplicationRunner {

    private final CostBenchmarkImportService importService;
    private final CostBenchmarkMappingService mappingService;

    @Override
    public void run(ApplicationArguments args) {

        int importedCount = importService.importBenchmarks();

        int mappedCount = mappingService.initializeMappings();

        System.out.println(
                "KOSIS 운영비 데이터 저장 완료: " + importedCount + "개"
        );

        System.out.println(
                "이리ON 업종 매핑 완료: " + mappedCount + "개"
        );
    }
}
