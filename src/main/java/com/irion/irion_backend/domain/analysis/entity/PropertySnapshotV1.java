package com.irion.irion_backend.domain.analysis.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.irion.irion_backend.domain.property.enums.MappingStatus;

import java.math.BigDecimal;
import java.util.Objects;

public record PropertySnapshotV1(
        int schemaVersion,
        Long candidatePropertyId,
        String name,
        String address,
        String addressDetail,
        Double latitude,
        Double longitude,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal areaM2,
        Long deposit,
        Long monthlyRent,
        Long keyMoney,
        Long monthlyManagementFee,
        String regionCode,
        MappingStatus mappingStatus,
        String commercialAreaCode
) {
    public PropertySnapshotV1 {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Expected property snapshot schemaVersion 1");
        }
        Objects.requireNonNull(candidatePropertyId, "candidatePropertyId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
        Objects.requireNonNull(areaM2, "areaM2");
        Objects.requireNonNull(deposit, "deposit");
        Objects.requireNonNull(monthlyRent, "monthlyRent");
        Objects.requireNonNull(keyMoney, "keyMoney");
        Objects.requireNonNull(monthlyManagementFee, "monthlyManagementFee");
        Objects.requireNonNull(mappingStatus, "mappingStatus");
    }
}
