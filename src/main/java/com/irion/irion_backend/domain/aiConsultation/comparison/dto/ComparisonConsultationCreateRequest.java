package com.irion.irion_backend.domain.aiConsultation.comparison.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ComparisonConsultationCreateRequest(
        @NotNull @Size(min = 2, max = 3) @JsonDeserialize(contentUsing = IdDeserializer.class)
        List<@NotNull @Positive Long> propertyAnalysisIds) {
    public static class IdDeserializer extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
                return context.reportInputMismatch(Long.class, "Expected an integer property analysis ID");
            return parser.getLongValue();
        }
    }
}
