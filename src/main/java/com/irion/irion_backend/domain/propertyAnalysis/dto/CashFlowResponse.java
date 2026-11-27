package com.irion.irion_backend.domain.propertyAnalysis.dto;

import com.irion.irion_backend.domain.analysis.entity.MonthlyCashFlow;
import java.util.List;

public record CashFlowResponse(Long stressTestId, int salesDeclineRate, List<Month> cashFlows) {
    public record Month(int monthIndex, Long openingCash, Long sales, Long variableCost, Long fixedCost,
                        Long loanInterest, Long loanPrincipal, Long netCashFlow, Long closingCash) {
        public static Month from(MonthlyCashFlow row) {
            return new Month(row.getMonthIndex(), row.getOpeningCash(), row.getSales(), row.getVariableCost(),
                    row.getFixedCost(), row.getLoanInterest(), row.getPrincipalRepayment(), row.getNetCashFlow(),
                    row.getClosingCash());
        }
    }
}
