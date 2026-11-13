package com.irion.irion_backend.domain.commercialArea.entity;

import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "commercial_areas")
public class CommercialArea extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_system")
    private String codeSystem;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "region_code")
    private String regionCode;
}
