package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.AdministrativeRegionPO;
import com.urbansidequest.backend.mapper.AdministrativeRegionMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdministrativeRegionManage extends ServiceImpl<AdministrativeRegionMapper, AdministrativeRegionPO> {

    public List<AdministrativeRegionPO> findEnabledChildren(String parentAdcode) {
        return this.baseMapper.findEnabledChildren(parentAdcode);
    }

    public AdministrativeRegionPO findEnabledByAdcode(String adcode) {
        return this.baseMapper.findEnabledByAdcode(adcode);
    }

    public boolean hasEnabledChildren(String parentAdcode) {
        return this.baseMapper.hasEnabledChildren(parentAdcode);
    }

    public void upsert(AdministrativeRegionPO region) {
        this.baseMapper.upsert(region);
    }

    public void markChildrenLoaded(String adcode) {
        this.baseMapper.markChildrenLoaded(adcode);
    }
}
