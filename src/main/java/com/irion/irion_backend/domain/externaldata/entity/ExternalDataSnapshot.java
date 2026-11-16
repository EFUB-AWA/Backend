package com.irion.irion_backend.domain.externaldata.entity;

import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "external_data_snapshots")
public class ExternalDataSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name", nullable = false, length = 50)
    private String sourceName;

    @Column(name = "dataset_code", nullable = false, length = 100)
    private String datasetCode;

    @Column(name = "reference_year", nullable = false)
    private Integer referenceYear;

    //실제 외부 데이터 수집한 시각
    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "processing_version", nullable = false, length = 30)
    private String processingVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_payload", nullable = false, columnDefinition = "jsonb")
    private String dataPayload;


    public static ExternalDataSnapshot create(
            String sourceName,
            String datasetCode,
            Integer referenceYear,
            String dataPayload
    ) {
        ExternalDataSnapshot snapshot = new ExternalDataSnapshot();

        snapshot.sourceName = sourceName;
        snapshot.datasetCode = datasetCode;
        snapshot.referenceYear = referenceYear;
        snapshot.fetchedAt = java.time.LocalDateTime.now();
        snapshot.processingVersion = "v1";
        snapshot.dataPayload = dataPayload;

        return snapshot;
    }

}
