package com.irion.irion_backend.domain.commercialArea.service;

import com.irion.irion_backend.domain.commercialArea.dto.CommercialAreaMatch;
import com.irion.irion_backend.domain.commercialArea.entity.CommercialArea;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialAreaService {

    private final JsonMapper jsonMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final List<CommercialArea> areas = new ArrayList<>();

    record CommercialArea(
            String code,
            String name,
            String type,
            String regionCode,
            Geometry geometry
    ) {}

    @PostConstruct
    public void loadAreas() throws Exception {

        ClassPathResource resource = new ClassPathResource("data/commercial-area.geojson");
        GeoJsonReader reader = new GeoJsonReader();

        try (InputStream input = resource.getInputStream()) {

            JsonNode root = jsonMapper.readTree(input);

            for (JsonNode feature : root.get("features")) {

                JsonNode properties = feature.get("properties");

                String code = properties.get("TRDAR_CD").asText();
                String name = properties.get("TRDAR_CD_N").asText();
                String type = properties.get("TRDAR_SE_1").asText();
                String regionCode = properties.get("SIGNGU_CD").asText();

                Geometry geometry = reader.read(feature.get("geometry").toString());

                areas.add(new CommercialArea(code, name, type, regionCode, geometry));
            }
        }
    }

    public Optional<CommercialAreaMatch> findCommercialArea(double latitude, double longitude) {
        Point point = geometryFactory.createPoint(
                new Coordinate(longitude, latitude)
        );

        log.debug("상권 검색 시작: {}, {}", latitude, longitude);
        log.debug("로드된 상권 수: {}", areas.size());

        CommercialArea touristArea = null;

        for (CommercialArea area : areas) {
            if (!area.geometry().covers(point)) continue;

            if (!"관광특구".equals(area.type())) {
                return Optional.of(toMatch(area));
            }
            touristArea = area;
        }

        return Optional.ofNullable(touristArea).map(this::toMatch);
    }

    private CommercialAreaMatch toMatch(CommercialArea area) {
        return new CommercialAreaMatch(
                area.code(),
                area.name(),
                area.regionCode()
        );
    }
}
