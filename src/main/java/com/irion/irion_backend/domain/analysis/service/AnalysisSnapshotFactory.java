package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@Component
public class AnalysisSnapshotFactory {

    public Map<String, Object> conditionSnapshot(StartupCondition condition) {
        return Map.ofEntries(
                entry("schemaVersion", 1),
                entry("id", condition.getId()),
                entry("userId", condition.getUser().getId()),
                entry("industryId", condition.getIndustry().getId()),
                entry("title", condition.getTitle()),
                entry("preferredRegionCode", condition.getPreferredRegionCode()),
                entry("totalCapital", condition.getTotalCapital()),
                entry("availableCapital", condition.getAvailableCapital()),
                entry("loanAmount", condition.getLoanAmount()),
                entry("monthlyLaborCost", condition.getMonthlyLaborCost()),
                entry("costOfSalesRate", condition.getCostOfSalesRate().toPlainString()),
                entry("monthlyOtherOperatingCost", condition.getMonthlyOtherOperatingCost()));
    }

    public List<PropertySnapshotV1> propertySnapshots(List<CandidateProperty> properties) {
        return properties.stream().map(property -> new PropertySnapshotV1(
                1, property.getId(), property.getName(), property.getAddress(), property.getAddressDetail(),
                property.getLatitude(), property.getLongitude(), property.getAreaM2(), property.getDeposit(),
                property.getMonthlyRent(), property.getKeyMoney(), property.getMonthlyManagementFee(),
                property.getRegionCode(), property.getMappingStatus(), property.getCommercialAreaCode()
        )).toList();
    }
}
