package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.dto.WorkerPopulationStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.WORKER_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readNullableLong;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readRequiredCode;

@Service
@RequiredArgsConstructor
public class CommercialWorkerPopulationService {

    private static final String SERVICE_NAME = WORKER_POPULATION.serviceName();
    private static final int PAGE_SIZE = 1000;

    private final SeoulOpenApiClient seoulOpenApiClient;

    public Optional<WorkerPopulationStatistics> findStatistics(String quarterCode, String commercialAreaCode) {
        if (quarterCode == null || !quarterCode.matches("[0-9]{4}[1-4]")) {
            throw new IllegalArgumentException("기준 분기 코드는 연도 4자리와 분기 1자리로 입력해야 합니다.");
        }
        if (commercialAreaCode == null || commercialAreaCode.isBlank()) {
            throw new IllegalArgumentException("상권 코드는 필수입니다.");
        }

        List<WorkerPopulationStatistics> matches = new ArrayList<>(1);
        // 직장인구 API는 분기 필터를 적용하지 않아 응답에서 분기와 상권을 함께 비교합니다.
        seoulOpenApiClient.forEachPage(SERVICE_NAME, PAGE_SIZE, rows -> {
            for (JsonNode row : rows) {
                if (!quarterCode.equals(readRequiredCode(row, "STDR_YYQU_CD"))
                        || !commercialAreaCode.equals(readRequiredCode(row, "TRDAR_CD"))) {
                    continue;
                }
                if (!matches.isEmpty()) {
                    throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                }

                matches.add(toStatistics(row, quarterCode, commercialAreaCode));
            }
        });

        return matches.stream().findFirst();
    }

    WorkerPopulationStatistics toStatistics(JsonNode row, String quarterCode, String commercialAreaCode) {
        return new WorkerPopulationStatistics(
                quarterCode,
                commercialAreaCode,
                readNullableLong(row, "TOT_WRC_POPLTN_CO"),
                readNullableLong(row, "ML_WRC_POPLTN_CO"),
                readNullableLong(row, "FML_WRC_POPLTN_CO"),
                readNullableLong(row, "AGRDE_10_WRC_POPLTN_CO"),
                readNullableLong(row, "AGRDE_20_WRC_POPLTN_CO"),
                readNullableLong(row, "AGRDE_30_WRC_POPLTN_CO"),
                readNullableLong(row, "AGRDE_40_WRC_POPLTN_CO"),
                readNullableLong(row, "AGRDE_50_WRC_POPLTN_CO"),
                readNullableLong(row, "AGRDE_60_ABOVE_WRC_POPLTN_CO")
        );
    }
}
