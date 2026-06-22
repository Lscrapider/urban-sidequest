package com.urbansidequest.backend.manage;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.PoiRecallPlanConfigPO;
import com.urbansidequest.backend.mapper.PoiRecallPlanConfigMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PoiRecallPlanConfigManage extends ServiceImpl<PoiRecallPlanConfigMapper, PoiRecallPlanConfigPO> {

    public List<PoiRecallPlanConfigPO> findEnabledInterestPlansByTagCodes(List<String> tagCodes) {
        if (CollUtil.isEmpty(tagCodes)) {
            return List.of();
        }
        return this.baseMapper.findEnabledInterestPlansByTagCodes(tagCodes);
    }

    public List<PoiRecallPlanConfigPO> findEnabledPlansByPlanTypes(List<String> planTypes) {
        if (CollUtil.isEmpty(planTypes)) {
            return List.of();
        }
        return this.baseMapper.findEnabledPlansByPlanTypes(planTypes);
    }
}
