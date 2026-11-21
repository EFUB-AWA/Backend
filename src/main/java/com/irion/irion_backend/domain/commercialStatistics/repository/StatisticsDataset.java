package com.irion.irion_backend.domain.commercialStatistics.repository;

public enum StatisticsDataset {
    SALES("sales", "VwsmTrdarSelngQq"),
    STORES("stores", "VwsmTrdarStorQq"),
    FLOATING_POPULATION("floating-population", "VwsmTrdarFlpopQq"),
    RESIDENT_POPULATION("resident-population", "VwsmTrdarRepopQq"),
    WORKER_POPULATION("worker-population", "VwsmTrdarWrcPopltnQq"),
    CONSUMPTION("consumption", "trdarNcmCnsmp");

    private final String cacheName;
    private final String serviceName;

    StatisticsDataset(String cacheName, String serviceName) {
        this.cacheName = cacheName;
        this.serviceName = serviceName;
    }

    String cacheName() {
        return cacheName;
    }

    public String serviceName() {
        return serviceName;
    }
}
