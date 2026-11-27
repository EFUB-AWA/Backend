package com.irion.irion_backend.domain.analysis.entity;

import com.irion.irion_backend.domain.analysis.dto.StressTestResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "monthly_cash_flows", uniqueConstraints = @UniqueConstraint(columnNames = {"stress_test_id", "month_index"}))
public class MonthlyCashFlow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stress_test_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StressTest stressTest;

    @Column(name = "month_index", nullable = false)
    private int monthIndex;

    @Column(name = "opening_cash", nullable = false)
    private long openingCash;

    @Column(name = "sales", nullable = false)
    private long sales;

    @Column(name = "variable_cost", nullable = false)
    private long variableCost;

    @Column(name = "fixed_cost", nullable = false)
    private long fixedCost;

    @Column(name = "loan_interest", nullable = false)
    private long loanInterest;

    @Column(name = "principal_repayment", nullable = false)
    private long principalRepayment;

    @Column(name = "net_cash_flow", nullable = false)
    private long netCashFlow;

    @Column(name = "closing_cash", nullable = false)
    private long closingCash;

    public MonthlyCashFlow(StressTest stressTest, StressTestResult.Month result) {
        this.stressTest = stressTest;
        this.monthIndex = result.monthIndex();
        this.openingCash = result.openingCash();
        this.sales = result.sales();
        this.variableCost = result.variableCost();
        this.fixedCost = result.fixedCost();
        this.loanInterest = result.loanInterest();
        this.principalRepayment = result.principalRepayment();
        this.netCashFlow = result.netCashFlow();
        this.closingCash = result.closingCash();
    }
}
