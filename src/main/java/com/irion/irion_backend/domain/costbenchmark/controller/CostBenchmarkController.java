package com.irion.irion_backend.domain.costbenchmark.controller;

import com.irion.irion_backend.domain.costbenchmark.dto.CostBenchmarkResponse;
import com.irion.irion_backend.domain.costbenchmark.service.CostBenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cost-benchmarks")
public class CostBenchmarkController {

    private final CostBenchmarkService costBenchmarkService;

    @GetMapping
    public ResponseEntity<CostBenchmarkResponse> getCostBenchmark(@RequestParam Long industryId) {
        return ResponseEntity.ok(costBenchmarkService.getCostBenchmark(industryId));
    }
}

