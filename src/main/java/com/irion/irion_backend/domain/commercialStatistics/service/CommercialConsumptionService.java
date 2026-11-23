package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.dto.ConsumptionStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.CONSUMPTION;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readNullableDecimal;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readRequiredCode;

@Service
@RequiredArgsConstructor
public class CommercialConsumptionService {

    private static final String SERVICE_NAME = CONSUMPTION.serviceName();
    private static final int PAGE_SIZE = 1000;

    private final SeoulOpenApiClient seoulOpenApiClient;

    public Optional<ConsumptionStatistics> findStatistics(String quarterCode, String commercialAreaCode) {
        if (quarterCode == null || !quarterCode.matches("[0-9]{4}[1-4]")) {
            throw new IllegalArgumentException("기준 분기 코드는 연도 4자리와 분기 1자리로 입력해야 합니다.");
        }
        if (commercialAreaCode == null || commercialAreaCode.isBlank()) {
            throw new IllegalArgumentException("상권 코드는 필수입니다.");
        }

        List<ConsumptionStatistics> matches = new ArrayList<>(1);
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

    ConsumptionStatistics toStatistics(JsonNode row, String quarterCode, String commercialAreaCode) {
        return new ConsumptionStatistics(
                quarterCode,
                commercialAreaCode,
                readNullableDecimal(row, "EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "FDSTFFS_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "CLTHS_FTWR_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "LVSPL_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "MCP_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "TRNSPORT_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "LSR_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "CLTUR_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "EDC_EXPNDTR_TOTAMT"),
                readNullableDecimal(row, "PLESR_EXPNDTR_TOTAMT")
        );
    }
}
