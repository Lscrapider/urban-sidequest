package com.urbansidequest.backend.domain.vo;

/** 移动端地区选择器使用的行政区节点。 */
public record AdministrativeRegionVO(
        String adcode,
        String parentAdcode,
        String name,
        String level,
        boolean selectable,
        boolean hasChildren,
        String routeCityName,
        String routeCityAdcode,
        GeoPointVO center
) {
}
