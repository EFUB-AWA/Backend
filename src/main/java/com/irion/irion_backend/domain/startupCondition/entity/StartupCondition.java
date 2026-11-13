package com.irion.irion_backend.domain.startupCondition.entity;

import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "startup_conditions")
public class StartupCondition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_id", nullable = false)
    private Industry industry;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "preferred_region_code", nullable = false)
    private String preferredRegionCode;

    @Column(name = "planned_opening_month", nullable = false)
    private LocalDate plannedOpeningMonth;

    @Column(name = "total_capital", nullable = false)
    private Long totalCapital;

    @Column(name = "available_capital", nullable = false)
    private Long availableCapital;

}
