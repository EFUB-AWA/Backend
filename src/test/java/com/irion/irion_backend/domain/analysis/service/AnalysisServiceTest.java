package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.aiConsultation.property.repository.PropertyConsultationRepository;
import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
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
import com.irion.irion_backend.domain.analysis.dto.AnalysisHistoryResponse.AnalysisSummary;
import com.irion.irion_backend.domain.analysis.dto.AnalysisHistoryResponse.PropertySummary;
import com.irion.irion_backend.domain.user.repository.UserRepository;
import static com.irion.irion_backend.domain.user.exception.UserErrorCode.USER_NOT_FOUND;
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
    private final UserRepository users = mock(UserRepository.class);
    private final PropertyConsultationRepository consultations = mock(PropertyConsultationRepository.class);
    private final AnalysisService statusService = new AnalysisService(mock(AnalysisRequestValidator.class),
            new AnalysisSnapshotFactory(), runs, events, results, users, consultations, mock(jakarta.persistence.EntityManager.class));

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = {"COMPLETED", "FAILED"})
    void deletesChildrenBeforeRunEvenIfEmpty(AnalysisStatus status) {
        var run = storedRun();
        when(run.getStatus()).thenReturn(status);
        when(runs.findForUpdate(100L)).thenReturn(Optional.of(run));
        statusService.delete(7L, 100L);
        var order = inOrder(runs, results, consultations);
        order.verify(runs).findForUpdate(100L);
        order.verify(results).lockForDeletion(100L);
        order.verify(consultations).lockForDeletion(100L);
        order.verify(consultations).deleteForRun(100L);
        order.verify(results).deleteForRun(100L);
        order.verify(runs).delete(run);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void deletingUnknownIdStopsAtLookup(long id) {
        assertEquals(AnalysisErrorCode.ANALYSIS_NOT_FOUND,
                assertThrows(BusinessException.class, () -> statusService.delete(7L, id)).getErrorCode());
        verifyNoInteractions(results, consultations);
        verify(runs, never()).delete(any());
    }

    @ParameterizedTest
    @CsvSource({"7,ANALYSIS_IN_PROGRESS", "8,ANALYSIS_ACCESS_DENIED"})
    void checksOwnerBeforeProcessingState(long requester, AnalysisErrorCode expected) {
        var run = storedRun();
        when(run.getStatus()).thenReturn(AnalysisStatus.PROCESSING);
        when(runs.findForUpdate(100L)).thenReturn(Optional.of(run));
        assertEquals(expected, assertThrows(BusinessException.class, () -> statusService.delete(requester, 100L)).getErrorCode());
        verifyNoInteractions(results, consultations);
        verify(runs, never()).delete(any());
    }

    @Test
    void pendingConsultationStopsBeforeFirstDelete() {
        var run = completedRun(1L);
        when(runs.findForUpdate(100L)).thenReturn(Optional.of(run));
        var pending = mock(PropertyConsultation.class);
        when(pending.getStatus()).thenReturn(ConsultationStatus.PENDING);
        when(consultations.lockForDeletion(100L)).thenReturn(List.of(pending));
        assertEquals(AnalysisErrorCode.PROPERTY_CONSULTATION_IN_PROGRESS,
                assertThrows(BusinessException.class, () -> statusService.delete(7L, 100L)).getErrorCode());
        verify(consultations, never()).deleteForRun(any());
        verify(results, never()).deleteForRun(any());
        verify(runs, never()).delete(any());
    }

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

    @Test
    void historyDistinguishesUnknownUserFromEmptyHistory() {
        assertSame(USER_NOT_FOUND, assertThrows(BusinessException.class, () -> statusService.getHistory(7L)).getErrorCode());
        verifyNoInteractions(runs, results);
        when(users.existsById(7L)).thenReturn(true);
        assertEquals(List.of(), statusService.getHistory(7L).analyses());
        verify(runs).findByStartupCondition_User_IdOrderByCreatedAtDescIdDesc(7L);
        verifyNoInteractions(results);
    }

    private AnalysisRun historyRun(long id, AnalysisStatus status, Long... candidates) {
        var run = completedRun(candidates);
        when(run.getId()).thenReturn(id);
        when(run.getStatus()).thenReturn(status);
        return run;
    }

    @Test
    void historyGroupsRowsWithinRunsAndPreservesBothOrders() {
        var first = historyRun(5, AnalysisStatus.COMPLETED, 12L, 11L);
        var pending = historyRun(4, AnalysisStatus.PROCESSING, 11L);
        var failed = historyRun(3, AnalysisStatus.FAILED, 11L);
        var older = historyRun(2, AnalysisStatus.COMPLETED, 11L);
        var time = LocalDateTime.of(2026, 11, 10, 12, 0);
        when(first.getCompletedAt()).thenReturn(time);
        when(pending.getCompletedAt()).thenReturn(time);
        when(failed.getCompletedAt()).thenReturn(time);
        var a = row(11, 20, 1);
        var b = row(12, 21, 2);
        var c = row(11, 22, 1);
        when(a.getAnalysisRun()).thenReturn(first);
        when(b.getAnalysisRun()).thenReturn(first);
        when(c.getAnalysisRun()).thenReturn(older);
        when(c.getSafetyGrade()).thenReturn("WARNING");
        when(b.getPropertySnapshot()).thenReturn(Map.of("address", "address12"));
        when(users.existsById(7L)).thenReturn(true);
        when(runs.findByStartupCondition_User_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(first, pending, failed, older));
        var ids = List.of(5L, 4L, 3L, 2L);
        when(results.findByAnalysisRun_IdIn(ids)).thenReturn(List.of(c, a, b));

        var history = statusService.getHistory(7L).analyses();
        assertEquals(ids, history.stream().map(AnalysisSummary::analysisRunId).toList());
        assertEquals(List.of(new PropertySummary(b.getId(), 12L, "name12", "address12", "A"),
                new PropertySummary(a.getId(), 11L, "name11", "address11", "A")), history.get(0).properties());
        assertEquals(time, history.get(0).completedAt());
        assertNull(history.get(3).completedAt());
        assertEquals("C", history.get(3).properties().get(0).safetyGrade());
        for (var summary : history.subList(1, 3)) {
            assertNull(summary.title());
            assertNull(summary.completedAt());
            assertEquals(List.of(new PropertySummary(null, 11L, "name11", "address11", null)), summary.properties());
        }
        verify(results).findByAnalysisRun_IdIn(ids);
        verifyNoMoreInteractions(results);
        verify(first, never()).getStartupCondition();
        verify(a.getCandidateProperty(), never()).getName();
        verify(a.getCandidateProperty(), never()).getAddress();
    }

    @ParameterizedTest
    @CsvSource({"A,A", "' stable ',A", "안정,A", "B,B", "CaUtIoN,B", "주의,B", "C,C", "warning,C", "경고,C"})
    void historyNormalizesStoredGrades(String stored, String expected) {
        var run = completedRun(11L);
        var row = row(11, 20, 1);
        when(row.getSafetyGrade()).thenReturn(stored);
        assertEquals(expected, AnalysisSummary.from(run, List.of(row)).properties().get(0).safetyGrade());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null ids", "empty ids", "null id", "duplicate id", "duplicate snapshots", "absent snapshots",
            "wrong snapshot", "missing result", "duplicate result", "extra result", "fk conflict", "name conflict",
            "address conflict", "missing text", "null grade", "unsupported grade", "processing result", "failed result", "no status"})
    void historyRejectsCorruptRun(String issue) {
        var run = completedRun(11L);
        var row = row(11, 20, 1);
        var rows = List.of(row);
        var snapshot = run.getPropertySnapshots().get(0);
        switch (issue) {
            case "null ids" -> when(run.getCandidatePropertyIds()).thenReturn(null);
            case "empty ids" -> when(run.getCandidatePropertyIds()).thenReturn(List.of());
            case "null id" -> when(run.getCandidatePropertyIds()).thenReturn(java.util.Collections.singletonList(null));
            case "duplicate id" -> when(run.getCandidatePropertyIds()).thenReturn(List.of(11L, 11L));
            case "duplicate snapshots" -> when(run.getPropertySnapshots()).thenReturn(List.of(snapshot, snapshot));
            case "absent snapshots" -> when(run.getPropertySnapshots()).thenReturn(null);
            case "wrong snapshot" -> when(snapshot.candidatePropertyId()).thenReturn(12L);
            case "missing result" -> rows = List.of();
            case "duplicate result" -> rows = List.of(row, row);
            case "extra result" -> rows = List.of(row, row(12, 21, 2));
            case "fk conflict" -> when(row.getPropertySnapshot()).thenReturn(Map.of("candidatePropertyId", 12));
            case "name conflict" -> when(row.getPropertySnapshot()).thenReturn(Map.of("name", "other"));
            case "address conflict" -> when(row.getPropertySnapshot()).thenReturn(Map.of("address", "other"));
            case "missing text" -> when(snapshot.address()).thenReturn(null);
            case "null grade" -> when(row.getSafetyGrade()).thenReturn(null);
            case "unsupported grade" -> when(row.getSafetyGrade()).thenReturn("other");
            case "processing result" -> when(run.getStatus()).thenReturn(AnalysisStatus.PROCESSING);
            case "failed result" -> when(run.getStatus()).thenReturn(AnalysisStatus.FAILED);
            case "no status" -> when(run.getStatus()).thenReturn(null);
        }
        var storedRows = rows;
        assertThrows(RuntimeException.class, () -> AnalysisSummary.from(run, storedRows));
    }

    @Test
    void historyPropagatesLookupFailure() {
        when(users.existsById(7L)).thenReturn(true);
        var error = new IllegalStateException("History lookup failed");
        when(runs.findByStartupCondition_User_IdOrderByCreatedAtDescIdDesc(7L)).thenThrow(error);
        assertSame(error, assertThrows(IllegalStateException.class, () -> statusService.getHistory(7L)));
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
                mock(org.springframework.context.ApplicationEventPublisher.class), results, users, consultations, mock(jakarta.persistence.EntityManager.class));
        var request = new AnalysisRunRequest(1L, List.of(2L), "analysis");
        when(validator.validate(3L, request)).thenThrow(new BusinessException(code));
        assertSame(code, assertThrows(BusinessException.class, () -> service.start(3L, request)).getErrorCode());
        verifyNoInteractions(repository);
    }
}
