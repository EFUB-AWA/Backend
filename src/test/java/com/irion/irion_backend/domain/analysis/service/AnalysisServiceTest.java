package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.irion.irion_backend.domain.analysis.dto.AnalysisDetailResponse;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.analysis.repository.PropertyAnalysisRepository;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {
    private final AnalysisRunRepository runs = mock(AnalysisRunRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PropertyAnalysisRepository results = mock(PropertyAnalysisRepository.class);
    private final AnalysisService statusService = new AnalysisService(mock(AnalysisRequestValidator.class),
            new AnalysisSnapshotFactory(), runs, events, results);

    private static final Map<String, Object> CONDITION = Map.of("industryId", 3, "preferredRegionCode", "11680",
            "totalCapital", 5_000_000_000L, "availableCapital", 70, "loanAmount", 30);

    private PropertySnapshotV1 snapshot(long id) {
        var snapshot = mock(PropertySnapshotV1.class);
        when(snapshot.candidatePropertyId()).thenReturn(id);
        when(snapshot.name()).thenReturn("name" + id);
        when(snapshot.address()).thenReturn("address" + id);
        return snapshot;
    }

    private AnalysisRun completedRun(Long... ids) {
        var run = storedRun();
        when(run.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(run.getConditionSnapshot()).thenReturn(CONDITION);
        when(run.getCandidatePropertyIds()).thenReturn(List.of(ids));
        var snapshots = List.of(ids).stream().map(this::snapshot).toList();
        when(run.getPropertySnapshots()).thenReturn(snapshots);
        return run;
    }

    private PropertyAnalysis row(long candidateId, long resultId, Integer rank) {
        var property = mock(CandidateProperty.class);
        when(property.getId()).thenReturn(candidateId);
        var row = mock(PropertyAnalysis.class);
        when(row.getCandidateProperty()).thenReturn(property);
        when(row.getId()).thenReturn(resultId);
        when(row.getPropertySnapshot()).thenReturn(Map.of("candidatePropertyId", candidateId, "name", "name" + candidateId));
        when(row.getExpectedMonthlySales()).thenReturn(80L);
        when(row.getMonthlyOperatingProfit()).thenReturn(-5L);
        when(row.getBreakEvenSales()).thenReturn(null);
        when(row.getBreakEvenStatus()).thenReturn("UNREACHABLE");
        when(row.getInitialRequiredCash()).thenReturn(60L);
        when(row.getSafetyScore()).thenReturn(new BigDecimal("82.5"));
        when(row.getSafetyGrade()).thenReturn("A");
        when(row.getRecommendationRank()).thenReturn(rank);
        return row;
    }

    @Test
    void readsStoredDetailByCandidateAndSortsTiesAndNulls() {
        var run = completedRun(11L, 12L, 13L, 14L);
        when(run.getHorizonMonths()).thenReturn(null);
        var rows = List.of(row(13, 23, 2), row(11, 21, null), row(14, 24, 1), row(12, 22, 2));
        when(results.findByAnalysisRun_Id(100L)).thenReturn(rows);
        var detail = statusService.getDetail(7L, 100L);
        assertEquals(List.of(24L, 22L, 23L, 21L), detail.properties().stream()
                .map(AnalysisDetailResponse.PropertySummary::propertyAnalysisId).toList());
        assertEquals(new AnalysisDetailResponse.PropertySummary(21L, 11L, "name11", "address11",
                80L, -5L, null, 60L, new BigDecimal("82.5"), "A", null, null), detail.properties().get(3));
        assertEquals(new AnalysisDetailResponse.StartupConditionSummary(3L, "11680", 5_000_000_000L,
                70L, 30L, null), detail.startupCondition());
        assertNull(detail.horizonMonths());
        verify(results).findByAnalysisRun_Id(100L);
        verifyNoMoreInteractions(results);
    }

    @ParameterizedTest
    @CsvSource({"A,A","' stable ',A","안정,A","B,B","caution,B","주의,B","C,C","warning,C","경고,C"})
    void normalizesStoredGrades(String stored, String expected) {
        completedRun(11L);
        var row = row(11, 21, 1);
        when(row.getSafetyGrade()).thenReturn(stored);
        when(results.findByAnalysisRun_Id(100L)).thenReturn(List.of(row));
        assertEquals(expected, statusService.getDetail(7L, 100L).properties().get(0).safetyGrade());
    }

    @ParameterizedTest
    @EnumSource(AnalysisStatus.class)
    void nonOwnerGetsForbiddenBeforeStateCheck(AnalysisStatus state) {
        var run = storedRun();
        when(run.getStatus()).thenReturn(state);
        assertSame(AnalysisErrorCode.ANALYSIS_ACCESS_DENIED, assertThrows(BusinessException.class,
                () -> statusService.getDetail(8L, 100L)).getErrorCode());
        verifyNoInteractions(results);
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = {"PROCESSING", "FAILED"})
    void unfinishedRunIsNotRead(AnalysisStatus state) {
        var run = storedRun();
        when(run.getStatus()).thenReturn(state);
        assertEquals("ANALYSIS_NOT_COMPLETED", assertThrows(BusinessException.class,
                () -> statusService.getDetail(7L, 100L)).getErrorCode().getCode());
        verifyNoInteractions(results);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 100L})
    void missingDetailDoesNotReadResults(long id) {
        assertSame(AnalysisErrorCode.ANALYSIS_NOT_FOUND, assertThrows(BusinessException.class,
                () -> statusService.getDetail(7L, id)).getErrorCode());
        verifyNoInteractions(results);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fraction", "overflow", "text", "missing", "negative"})
    void refusesInvalidConditionAmounts(String problem) {
        var run = completedRun(11L);
        var data = new HashMap<>(CONDITION);
        switch (problem) {
            case "fraction" -> data.put("totalCapital", new BigDecimal("10.9"));
            case "overflow" -> data.put("totalCapital", BigInteger.ONE.shiftLeft(63));
            case "text" -> data.put("totalCapital", "100");
            case "missing" -> data.remove("totalCapital");
            case "negative" -> data.put("totalCapital", -1);
        }
        when(run.getConditionSnapshot()).thenReturn(data);
        var row = row(11, 21, 1);
        when(results.findByAnalysisRun_Id(100L)).thenReturn(List.of(row));
        assertThrows(RuntimeException.class, () -> statusService.getDetail(7L, 100L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing result", "extra result", "duplicate result", "duplicate candidate",
            "missing snapshot", "duplicate snapshot", "foreign snapshot id", "conflicting address", "unknown grade",
            "missing grade", "missing sales", "missing profit", "missing cash", "missing score", "missing break-even"})
    void rejectsInconsistentStoredData(String problem) {
        var run = completedRun(11L);
        var row = row(11, 21, 1);
        var rows = List.of(row);
        switch (problem) {
            case "missing result" -> rows = List.of();
            case "extra result" -> rows = List.of(row, row(12, 22, 2));
            case "duplicate result" -> rows = List.of(row, row);
            case "duplicate candidate" -> when(run.getCandidatePropertyIds()).thenReturn(List.of(11L, 11L));
            case "missing snapshot" -> when(run.getPropertySnapshots()).thenReturn(List.of());
            case "duplicate snapshot" -> {
                var snapshot = snapshot(11);
                when(run.getPropertySnapshots()).thenReturn(List.of(snapshot, snapshot));
            }
            case "foreign snapshot id" -> when(row.getPropertySnapshot()).thenReturn(Map.of("candidatePropertyId", 12));
            case "conflicting address" -> when(row.getPropertySnapshot()).thenReturn(Map.of("address", "different"));
            case "unknown grade" -> when(row.getSafetyGrade()).thenReturn("other");
            case "missing grade" -> when(row.getSafetyGrade()).thenReturn(null);
            case "missing sales" -> when(row.getExpectedMonthlySales()).thenReturn(null);
            case "missing profit" -> when(row.getMonthlyOperatingProfit()).thenReturn(null);
            case "missing cash" -> when(row.getInitialRequiredCash()).thenReturn(null);
            case "missing score" -> when(row.getSafetyScore()).thenReturn(null);
            case "missing break-even" -> when(row.getBreakEvenStatus()).thenReturn("ACHIEVED");
        }
        when(results.findByAnalysisRun_Id(100L)).thenReturn(rows);
        assertThrows(RuntimeException.class, () -> statusService.getDetail(7L, 100L));
    }

    private AnalysisRun storedRun() {
        var owner = mock(User.class);
        var condition = mock(StartupCondition.class);
        var run = mock(AnalysisRun.class);
        when(owner.getId()).thenReturn(7L);
        when(condition.getUser()).thenReturn(owner);
        when(run.getStartupCondition()).thenReturn(condition);
        when(run.getId()).thenReturn(100L);
        when(runs.findById(100L)).thenReturn(Optional.of(run));
        return run;
    }

    @ParameterizedTest
    @EnumSource(AnalysisStatus.class)
    void readsOwnedStatusWithoutChangingRun(AnalysisStatus status) {
        var run = storedRun();
        var storedTime = LocalDateTime.of(2026, 11, 10, 12, 0);
        when(run.getStatus()).thenReturn(status);
        when(run.getCompletedAt()).thenReturn(storedTime);

        var response = statusService.getStatus(7L, 100L);

        assertEquals(100L, response.analysisRunId());
        assertEquals(status, response.status());
        assertEquals(status == AnalysisStatus.COMPLETED ? storedTime : null, response.completedAt());
        verify(runs).findById(100L);
        verifyNoMoreInteractions(runs);
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 8L)
    void rejectsNonOwner(Long requesterId) {
        storedRun();
        var error = assertThrows(BusinessException.class, () -> statusService.getStatus(requesterId, 100L));
        assertSame(AnalysisErrorCode.ANALYSIS_ACCESS_DENIED, error.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, 100L})
    void reportsMissingRun(long id) {
        var error = assertThrows(BusinessException.class, () -> statusService.getStatus(7L, id));
        assertSame(AnalysisErrorCode.ANALYSIS_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void propagatesRepositoryFailure() {
        var failure = new IllegalStateException("lookup failed");
        when(runs.findById(100L)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> statusService.getStatus(7L, 100L)));
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisErrorCode.class, names = {"ANALYSIS_ACCESS_DENIED", "PROPERTY_MAPPING_NOT_COMPLETED"})
    void validationFailureStopsBeforeSaving(AnalysisErrorCode code) {
        var validator = mock(AnalysisRequestValidator.class);
        var repository = mock(AnalysisRunRepository.class);
        var service = new AnalysisService(validator, new AnalysisSnapshotFactory(), repository,
                mock(org.springframework.context.ApplicationEventPublisher.class), results);
        var request = new AnalysisRunRequest(1L, List.of(2L), "analysis");
        when(validator.validate(3L, request)).thenThrow(new BusinessException(code));
        assertSame(code, assertThrows(BusinessException.class, () -> service.start(3L, request)).getErrorCode());
        verifyNoInteractions(repository);
    }
}
