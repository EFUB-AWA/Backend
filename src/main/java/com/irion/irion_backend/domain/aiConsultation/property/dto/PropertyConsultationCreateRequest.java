package com.irion.irion_backend.domain.aiConsultation.property.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PropertyConsultationCreateRequest(@NotNull @Positive Long propertyAnalysisId) {}
