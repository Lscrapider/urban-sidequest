package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.PoiSemanticMappingPO;
import com.urbansidequest.backend.mapper.PoiSemanticMappingMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PoiSemanticMappingManage extends ServiceImpl<PoiSemanticMappingMapper, PoiSemanticMappingPO> {

    public List<PoiSemanticMappingPO> findEnabledMappings() {
        return this.baseMapper.findEnabledMappings();
    }
}
