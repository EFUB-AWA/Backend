package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.dto.StoreStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readNullableDecimal;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readNullableLong;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readRequiredCode;

@Service
@RequiredArgsConstructor
public class CommercialStoreService {

    private static final String SERVICE_NAME = "VwsmTrdarStorQq";
    private static final int PAGE_SIZE = 1000;

    private final SeoulOpenApiClient seoulOpenApiClient;

    public Optional<StoreStatistics> findStatistics(String quarterCode, String commercialAreaCode, String industryCode) {
        if (quarterCode == null || !quarterCode.matches("[0-9]{4}[1-4]")) {
            throw new IllegalArgumentException("기준 분기 코드는 연도 4자리와 분기 1자리로 입력해야 합니다.");
        }
        if (commercialAreaCode == null || commercialAreaCode.isBlank()
                || industryCode == null || industryCode.isBlank()) {
            throw new IllegalArgumentException("상권 코드와 업종 코드는 필수입니다.");
        }

        List<StoreStatistics> matches = new ArrayList<>(1);
        seoulOpenApiClient.forEachPage(SERVICE_NAME, PAGE_SIZE, rows -> {
            for (JsonNode row : rows) {
                if (!quarterCode.equals(readRequiredCode(row, "STDR_YYQU_CD"))
                        || !commercialAreaCode.equals(readRequiredCode(row, "TRDAR_CD"))) {
                    throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                }
                if (!industryCode.equals(readRequiredCode(row, "SVC_INDUTY_CD"))) {
                    continue;
                }
                if (!matches.isEmpty()) {
                    throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                }

                matches.add(new StoreStatistics(
                        quarterCode,
                        commercialAreaCode,
                        industryCode,
                        readNullableLong(row, "SIMILR_INDUTY_STOR_CO"),
                        readNullableLong(row, "STOR_CO"),
                        readNullableLong(row, "FRC_STOR_CO"),
                        readNullableLong(row, "OPBIZ_STOR_CO"),
                        readNullableLong(row, "CLSBIZ_STOR_CO"),
                        readNullableDecimal(row, "OPBIZ_RT"),
                        readNullableDecimal(row, "CLSBIZ_RT")
                ));
            }
        }, quarterCode, commercialAreaCode);

        return matches.stream().findFirst();
    }
}
