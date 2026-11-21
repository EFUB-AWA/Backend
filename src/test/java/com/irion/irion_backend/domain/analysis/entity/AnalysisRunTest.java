package com.irion.irion_backend.domain.analysis.entity;

import com.irion.irion_backend.domain.property.enums.MappingStatus;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisRunTest {

    private PropertySnapshotV1 snapshot(long id) {
        return new PropertySnapshotV1(1, id, "매물", "주소", null, 37.5, 127.0,
                new BigDecimal("99999999.90"), 100L, 20L, 0L, 3L, null, MappingStatus.MAPPED, "A1");
    }

    private AnalysisRun create(List<Long> ids, List<PropertySnapshotV1> snapshots) {
        return AnalysisRun.create(StartupCondition.builder().build(), "분석", ids, Map.of("id", 1L), snapshots);
    }

    @Test
    void createsProcessingRunInRequestOrderIncludingDuplicates() {
        var ids = List.of(9L, 2L, 9L);
        var snapshots = List.of(snapshot(9), snapshot(2), snapshot(9));
        AnalysisRun run = create(ids, snapshots);
        assertEquals(AnalysisStatus.PROCESSING, run.getStatus());
        assertEquals(ids, run.getCandidatePropertyIds());
        assertEquals(snapshots, run.getPropertySnapshots());
    }

    @Test
    void rejectsNullList() {
        assertThrows(NullPointerException.class, () -> create(List.of(1L), null));
    }

    @Test
    void rejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> create(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(1L), List.of()));
    }

    @Test
    void rejectsNullElement() {
        assertThrows(NullPointerException.class, () -> create(List.of(1L), Collections.singletonList(null)));
    }

    @Test
    void rejectsDifferentLengths() {
        assertThrows(IllegalArgumentException.class, () -> create(List.of(1L, 1L), List.of(snapshot(1))));
    }

    @Test
    void rejectsDifferentOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> create(List.of(1L, 2L), List.of(snapshot(2), snapshot(1))));
    }

    @Test
    void protectsBothListsFromMutation() {
        var ids = new ArrayList<>(List.of(1L));
        var snapshots = new ArrayList<>(List.of(snapshot(1)));
        AnalysisRun run = create(ids, snapshots);
        ids.clear();
        snapshots.clear();
        assertEquals(List.of(1L), run.getCandidatePropertyIds());
        assertEquals(List.of(snapshot(1)), run.getPropertySnapshots());
        assertThrows(UnsupportedOperationException.class, () -> run.getCandidatePropertyIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> run.getPropertySnapshots().clear());
    }

    @Test
    void hibernateJsonRoundTripPreservesTypedRecordsOrderNullsAndDecimalScale() throws Exception {
        var mapper = new JacksonJsonFormatMapper();
        Type type = AnalysisRun.class.getDeclaredField("propertySnapshots").getGenericType();
        var snapshots = List.of(snapshot(7), snapshot(2), snapshot(7));
        String json = mapper.toString(snapshots, type);
        assertTrue(json.contains("\"areaM2\":\"99999999.90\""));
        assertTrue(json.contains("\"addressDetail\":null"));
        assertTrue(json.contains("\"regionCode\":null"));
        List<PropertySnapshotV1> restored = mapper.fromString(json, type);
        assertEquals(snapshots, restored);
    }
}
