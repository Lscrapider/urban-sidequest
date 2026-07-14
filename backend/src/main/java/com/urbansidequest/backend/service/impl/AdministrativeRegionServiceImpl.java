package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.api.amap.AmapApi;
import com.urbansidequest.backend.domain.dto.AmapAdministrativeRegionDTO;
import com.urbansidequest.backend.domain.po.AdministrativeRegionPO;
import com.urbansidequest.backend.domain.vo.AdministrativeRegionVO;
import com.urbansidequest.backend.domain.vo.GeoPointVO;
import com.urbansidequest.backend.manage.AdministrativeRegionManage;
import java.math.BigDecimal;
import com.urbansidequest.backend.service.AdministrativeRegionService;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 行政区数据按父节点懒加载到本地表，避免移动端每次打开选择器都访问第三方接口。
 */
@Service
public class AdministrativeRegionServiceImpl implements AdministrativeRegionService {

    private static final String ROOT_ADCODE = "100000";

    private static final String ROOT_KEYWORDS = "中国";

    private static final Pattern ADCODE_PATTERN = Pattern.compile("\\d{6}");

    private final AdministrativeRegionManage administrativeRegionManage;

    private final AmapApi amapApi;

    private final Object regionLoadLock = new Object();

    public AdministrativeRegionServiceImpl(
            AdministrativeRegionManage administrativeRegionManage,
            AmapApi amapApi
    ) {
        this.administrativeRegionManage = administrativeRegionManage;
        this.amapApi = amapApi;
    }

    @Override
    public List<AdministrativeRegionVO> listRegions(String parentAdcode) {
        String resolvedParentAdcode = parentAdcode == null || parentAdcode.isBlank()
                ? ROOT_ADCODE
                : parentAdcode;
        if (!ADCODE_PATTERN.matcher(resolvedParentAdcode).matches()) {
            throw new IllegalArgumentException("地区编码格式不正确");
        }
        this.ensureChildrenLoaded(resolvedParentAdcode);
        return this.administrativeRegionManage.findEnabledChildren(resolvedParentAdcode)
                .stream()
                .map(this::toView)
                .toList();
    }

    private void ensureChildrenLoaded(String parentAdcode) {
        AdministrativeRegionPO parent = this.administrativeRegionManage.findEnabledByAdcode(parentAdcode);
        if (parent != null && Boolean.TRUE.equals(parent.getChildrenLoaded())) {
            return;
        }
        synchronized (this.regionLoadLock) {
            parent = this.administrativeRegionManage.findEnabledByAdcode(parentAdcode);
            if (parent != null && Boolean.TRUE.equals(parent.getChildrenLoaded())) {
                return;
            }
            List<AmapAdministrativeRegionDTO> roots = this.amapApi.queryAdministrativeRegions(
                    ROOT_ADCODE.equals(parentAdcode) ? ROOT_KEYWORDS : parentAdcode
            );
            AmapAdministrativeRegionDTO region = this.findRequestedRegion(roots, parentAdcode);
            if (region == null) {
                throw new IllegalArgumentException("未找到所选地区");
            }
            this.upsertRegion(
                    region,
                    ROOT_ADCODE.equals(parentAdcode) ? null : parent == null ? null : parent.getParentAdcode(),
                    0
            );
            int sortOrder = 0;
            for (AmapAdministrativeRegionDTO child : region.children()) {
                this.upsertRegion(child, region.adcode(), sortOrder++);
            }
            this.administrativeRegionManage.markChildrenLoaded(region.adcode());
        }
    }

    private AmapAdministrativeRegionDTO findRequestedRegion(
            List<AmapAdministrativeRegionDTO> roots,
            String parentAdcode
    ) {
        AmapAdministrativeRegionDTO requested = roots.stream()
                .filter(region -> parentAdcode.equals(region.adcode()))
                .findFirst()
                .orElse(null);
        if (requested != null || !ROOT_ADCODE.equals(parentAdcode) || roots.isEmpty()) {
            return requested;
        }
        return new AmapAdministrativeRegionDTO(
                ROOT_ADCODE,
                ROOT_KEYWORDS,
                "country",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                roots
        );
    }

    private void upsertRegion(
            AmapAdministrativeRegionDTO source,
            String parentAdcode,
            int sortOrder
    ) {
        AdministrativeRegionPO target = new AdministrativeRegionPO();
        target.setAdcode(source.adcode());
        target.setParentAdcode(parentAdcode);
        target.setName(source.name());
        target.setLevel(this.normalizeLevel(source.level()));
        target.setLongitudeGcj02(source.longitudeGcj02());
        target.setLatitudeGcj02(source.latitudeGcj02());
        target.setSelectable(!ROOT_ADCODE.equals(source.adcode()));
        target.setEnabled(true);
        target.setChildrenLoaded(false);
        target.setSortOrder(sortOrder);
        this.administrativeRegionManage.upsert(target);
    }

    private AdministrativeRegionVO toView(AdministrativeRegionPO region) {
        AdministrativeRegionPO routeCity = this.resolveRouteCity(region);
        return new AdministrativeRegionVO(
                region.getAdcode(),
                region.getParentAdcode(),
                region.getName(),
                region.getLevel(),
                Boolean.TRUE.equals(region.getSelectable()),
                !Boolean.TRUE.equals(region.getChildrenLoaded())
                        || this.administrativeRegionManage.hasEnabledChildren(region.getAdcode()),
                routeCity.getName(),
                routeCity.getAdcode(),
                new GeoPointVO(region.getLongitudeGcj02(), region.getLatitudeGcj02())
        );
    }

    private AdministrativeRegionPO resolveRouteCity(AdministrativeRegionPO region) {
        if (!"DISTRICT".equals(region.getLevel()) || region.getParentAdcode() == null) {
            return region;
        }
        AdministrativeRegionPO parent = this.administrativeRegionManage.findEnabledByAdcode(region.getParentAdcode());
        return parent == null ? region : parent;
    }

    private String normalizeLevel(String level) {
        return level == null || level.isBlank() ? "DISTRICT" : level.toUpperCase(Locale.ROOT);
    }
}
