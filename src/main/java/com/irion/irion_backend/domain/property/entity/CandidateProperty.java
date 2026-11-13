package com.irion.irion_backend.domain.property.entity;

import com.irion.irion_backend.domain.commercialArea.entity.CommercialArea;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "candidate_properties")
public class CandidateProperty extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: 추후 창업 조건 도메인 생성 후 수정 예정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "startup_condition_id", nullable = false)
    private StartupCondition startupCondition;

    // TODO: 추후 상업 지역 도메인 생성 후 수정 예정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_area_id")
    private CommercialArea commercialArea;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "address_detail")
    private String addressDetail;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name= "longitude", nullable = false)
    private Double longitude;

    @Column(name = "area_m2", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaM2;

    @Column(name = "deposit", nullable = false)
    private Long deposit;

    @Column(name = "monthly_rent", nullable = false)
    private Long monthlyRent;

    @Column(name = "key_money", nullable = false)
    private Long keyMoney;

    @Column(name = "monthly_management_fee", nullable = false)
    private Long monthlyManagementFee;

    @Column(name = "other_initial_cost", nullable = false)
    private Long otherInitialCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", nullable = false, length = 20)
    private MappingStatus mappingStatus;

    @Builder
    private CandidateProperty(StartupCondition startupCondition , String name, String address, String addressDetail,
                                    Double latitude, Double longitude, BigDecimal areaM2, Long deposit,
                                    Long monthlyRent, Long keyMoney, Long monthlyManagementFee,
                                    Long otherInitialCost) {
        this.startupCondition = startupCondition;
        this.commercialArea = null;
        this.name = name;
        this.address = address;
        this.addressDetail = addressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.areaM2 = areaM2;
        this.deposit = deposit;
        this.monthlyRent = monthlyRent;
        this.keyMoney = keyMoney;
        this.monthlyManagementFee = monthlyManagementFee;
        this.otherInitialCost = otherInitialCost;
        this.mappingStatus = MappingStatus.PENDING;
    }
}
