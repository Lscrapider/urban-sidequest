package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.domain.vo.AdministrativeRegionVO;
import com.urbansidequest.backend.service.AdministrativeRegionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
public class AdministrativeRegionController {

    private final AdministrativeRegionService administrativeRegionService;

    public AdministrativeRegionController(AdministrativeRegionService administrativeRegionService) {
        this.administrativeRegionService = administrativeRegionService;
    }

    @GetMapping
    public List<AdministrativeRegionVO> listRegions(
            @RequestParam(required = false) String parentAdcode
    ) {
        return this.administrativeRegionService.listRegions(parentAdcode);
    }
}
