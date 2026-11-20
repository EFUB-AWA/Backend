package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.global.error.BusinessException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;

final class SeoulStatisticsRow {

    private SeoulStatisticsRow() {}

    static String readRequiredCode(JsonNode row, String fieldName) {
        JsonNode field = row.path(fieldName);
        if (!field.isTextual() && !field.isIntegralNumber()) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }

        String value = field.asText();
        if (value.isBlank()) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
        return value;
    }

    static BigDecimal readNullableDecimal(JsonNode row, String fieldName) {
        JsonNode field = row.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        if (field.isNumber()) {
            return field.decimalValue();
        }
        if (!field.isTextual()) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }

        String value = field.asText().strip();
        if (value.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
    }

    static Long readNullableLong(JsonNode row, String fieldName) {
        BigDecimal value = readNullableDecimal(row, fieldName);
        if (value == null) {
            return null;
        }

        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
    }
}
