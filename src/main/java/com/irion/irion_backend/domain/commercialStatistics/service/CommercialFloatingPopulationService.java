package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.dto.FloatingPopulationStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.FLOATING_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readNullableLong;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readRequiredCode;

@Service
@RequiredArgsConstructor
public class CommercialFloatingPopulationService {

    private static final String SERVICE_NAME = FLOATING_POPULATION.serviceName();
    private static final int PAGE_SIZE = 1000;

    private final SeoulOpenApiClient seoulOpenApiClient;

    public Optional<FloatingPopulationStatistics> findStatistics(String quarterCode, String commercialAreaCode) {
        if (quarterCode == null || !quarterCode.matches("[0-9]{4}[1-4]")) {
            throw new IllegalArgumentException("기준 분기 코드는 연도 4자리와 분기 1자리로 입력해야 합니다.");
        }
        if (commercialAreaCode == null || commercialAreaCode.isBlank()) {
            throw new IllegalArgumentException("상권 코드는 필수입니다.");
        }

        List<FloatingPopulationStatistics> matches = new ArrayList<>(1);
        seoulOpenApiClient.forEachPage(SERVICE_NAME, PAGE_SIZE, rows -> {
            for (JsonNode row : rows) {
                if (!quarterCode.equals(readRequiredCode(row, "STDR_YYQU_CD"))) {
                    throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                }
                if (!commercialAreaCode.equals(readRequiredCode(row, "TRDAR_CD"))) {
                    continue;
                }
                if (!matches.isEmpty()) {
                    throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                }

                matches.add(toStatistics(row, quarterCode, commercialAreaCode));
            }
        }, quarterCode);

        return matches.stream().findFirst();
    }

    FloatingPopulationStatistics toStatistics(JsonNode row, String quarterCode, String commercialAreaCode) {
        return new FloatingPopulationStatistics(
                quarterCode,
                commercialAreaCode,
                readNullableLong(row, "TOT_FLPOP_CO"),
                readNullableLong(row, "ML_FLPOP_CO"),
                readNullableLong(row, "FML_FLPOP_CO"),
                readNullableLong(row, "AGRDE_10_FLPOP_CO"),
                readNullableLong(row, "AGRDE_20_FLPOP_CO"),
                readNullableLong(row, "AGRDE_30_FLPOP_CO"),
                readNullableLong(row, "AGRDE_40_FLPOP_CO"),
                readNullableLong(row, "AGRDE_50_FLPOP_CO"),
                readNullableLong(row, "AGRDE_60_ABOVE_FLPOP_CO"),
                readNullableLong(row, "TMZON_00_06_FLPOP_CO"),
                readNullableLong(row, "TMZON_06_11_FLPOP_CO"),
                readNullableLong(row, "TMZON_11_14_FLPOP_CO"),
                readNullableLong(row, "TMZON_14_17_FLPOP_CO"),
                readNullableLong(row, "TMZON_17_21_FLPOP_CO"),
                readNullableLong(row, "TMZON_21_24_FLPOP_CO")
        );
    }
}
