package com.irion.irion_backend.domain.aiConsultation.comparison.service;

import com.irion.irion_backend.domain.aiConsultation.comparison.client.ComparisonConsultationAiClient;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;
import com.irion.irion_backend.global.error.BusinessException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.AI_CONSULTATION_TIMEOUT;

class ComparisonReservationFailureTest {
    final ComparisonConsultationTransactions transactions = mock();
    final ComparisonConsultationAiClient ai = mock();
    final ComparisonConsultationService service = new ComparisonConsultationService(mock(),transactions,ai);
    final List<Long> ids = List.of(10L,2L);
    final ComparisonConsultationDetailResponse detail = mock();

    @Test
    void onlyNamedCombinationConflictTriggersFreshLookup() {
        var cause = mock(ConstraintViolationException.class);
        when(cause.getSQLState()).thenReturn("23505");
        when(cause.getConstraintName()).thenReturn("uq_comparison_user_key");
        var failure = new DataIntegrityViolationException("duplicate",cause);
        when(transactions.reserve(1L,ids,"2:10")).thenThrow(failure);
        when(transactions.reusable(1L,"2:10")).thenReturn(Optional.of(new ComparisonConsultationTransactions.Reservation(detail,null,1)));
        var response = service.create(1L,ids);
        assertFalse(response.created());
        assertSame(detail,response.detail());
        when(cause.getConstraintName()).thenReturn("some_other_constraint");
        assertSame(failure,assertThrows(DataIntegrityViolationException.class,() -> service.create(1L,ids)));
        verify(transactions).reusable(1L,"2:10");
        verifyNoInteractions(ai);
    }

    @Test
    void commitErrorsPropagateWithoutClaimingSuccessfulOrFailedGeneration() {
        var snapshot = mock(PropertySnapshot.class);
        when(snapshot.conditionSnapshot()).thenReturn(Map.of("industryId",1,"preferredRegionCode","11410",
                "totalCapital",100,"availableCapital",80,"loanAmount",20,"monthlyLaborCost",10,
                "monthlyOtherOperatingCost",2,"costOfSalesRate","30"));
        when(snapshot.calculationVersion()).thenReturn("1");
        when(snapshot.assumptionsSnapshot()).thenReturn(Map.of());
        when(detail.comparisonConsultId()).thenReturn(5L);
        when(transactions.reserve(1L,ids,"2:10")).thenReturn(
                new ComparisonConsultationTransactions.Reservation(detail,List.of(snapshot,snapshot),1));
        var dbFailure = new DataIntegrityViolationException("write failed");
        when(transactions.reserveRetry(1L,5L)).thenReturn(
                new ComparisonConsultationTransactions.Reservation(detail,List.of(snapshot,snapshot),2));
        when(transactions.complete(5L,2,null)).thenThrow(dbFailure);
        assertSame(dbFailure,assertThrows(DataIntegrityViolationException.class,() -> service.retry(1L,5L)));
        verify(transactions,never()).fail(5L,2);
        when(transactions.complete(5L,1,null)).thenThrow(dbFailure);
        assertSame(dbFailure,assertThrows(DataIntegrityViolationException.class,() -> service.create(1L,ids)));
        verify(transactions,never()).fail(5L,1);
        when(ai.generate(any())).thenThrow(new BusinessException(AI_CONSULTATION_TIMEOUT));
        doThrow(dbFailure).when(transactions).fail(5L,1);
        assertSame(dbFailure,assertThrows(DataIntegrityViolationException.class,() -> service.create(1L,ids)));
        doThrow(new IllegalStateException("unexpected AI failure")).when(ai).generate(any());
        assertSame(dbFailure,assertThrows(DataIntegrityViolationException.class,() -> service.create(1L,ids)));
        doThrow(dbFailure).when(transactions).fail(5L,2);
        assertSame(dbFailure,assertThrows(DataIntegrityViolationException.class,() -> service.retry(1L,5L)));
    }
}
