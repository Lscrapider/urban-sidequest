package com.urbansidequest.backend.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/** 高德行政区接口的最小归一化结果。 */
public record AmapAdministrativeRegionDTO(
        String adcode,
        String name,
        String level,
        BigDecimal longitudeGcj02,
        BigDecimal latitudeGcj02,
        List<AmapAdministrativeRegionDTO> children
) {
}
