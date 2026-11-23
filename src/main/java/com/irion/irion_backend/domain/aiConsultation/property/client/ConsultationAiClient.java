package com.irion.irion_backend.domain.aiConsultation.property.client;

import com.irion.irion_backend.domain.aiConsultation.property.entity.ImprovementSuggestion;
import com.irion.irion_backend.domain.aiConsultation.property.entity.RiskFactor;
import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;

import java.util.List;
import java.util.Map;

public interface ConsultationAiClient {
    record Input(Map<String, Object> condition, Map<String, Object> property, CalculatedPropertyResult analysis) {}
    record Result(String summary, List<RiskFactor> risks, List<ImprovementSuggestion> suggestions,
                  String considerations, String model, String promptVersion) {}

    Result generate(Input input);
}
