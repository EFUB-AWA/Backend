package com.irion.irion_backend.domain.industry.service;

import com.irion.irion_backend.domain.industry.dto.IndustryListResponse;
import com.irion.irion_backend.domain.industry.dto.IndustryResponse;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndustryService {

    private final IndustryRepository industryRepository;

    public IndustryListResponse getIndustries() {
        return new IndustryListResponse(
                industryRepository.findAll().stream()
                        .map(IndustryResponse::from)
                        .toList()
        );
    }
}
