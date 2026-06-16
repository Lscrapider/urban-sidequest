package com.urbansidequest.backend.manage;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.mapper.InterestTagCatalogMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InterestTagCatalogManage extends ServiceImpl<InterestTagCatalogMapper, InterestTagCatalogPO> {

    public List<InterestTagCatalogPO> findEnabledByTagCodes(List<String> tagCodes) {
        if (CollUtil.isEmpty(tagCodes)) {
            return List.of();
        }
        return this.baseMapper.findEnabledByTagCodes(tagCodes);
    }
}
