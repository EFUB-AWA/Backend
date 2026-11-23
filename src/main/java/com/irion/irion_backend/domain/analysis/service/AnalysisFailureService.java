package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisFailureService {
    private final AnalysisRunRepository runs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long runId, Throwable cause) {
        var run = runs.findForUpdate(runId).orElseThrow();
        if (run.getStatus() == AnalysisStatus.PROCESSING) {
            log.warn("Analysis run {} failed", runId, cause);
            run.fail();
        }
    }
}
