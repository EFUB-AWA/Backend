package com.irion.irion_backend.domain.externaldata.repository;

import com.irion.irion_backend.domain.externaldata.entity.ExternalDataSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalDataSnapshotRepository extends JpaRepository<ExternalDataSnapshot, Long> {
}
