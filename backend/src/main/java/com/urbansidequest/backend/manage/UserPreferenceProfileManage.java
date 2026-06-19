package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.po.UserPreferenceProfilePO;
import com.urbansidequest.backend.domain.po.UserPreferenceTagAffinityPO;
import com.urbansidequest.backend.mapper.UserPreferenceProfileMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserPreferenceProfileManage extends ServiceImpl<UserPreferenceProfileMapper, UserPreferenceProfilePO> {

    public UserPreferenceProfileDTO findProfileByUserId(UUID userId) {
        if (userId == null) {
            return UserPreferenceProfileDTO.empty();
        }
        UserPreferenceProfilePO profile = this.baseMapper.findByUserId(userId);
        if (profile == null) {
            return UserPreferenceProfileDTO.empty();
        }
        List<UserPreferenceTagAffinityPO> affinities = this.baseMapper.findEnabledTagAffinitiesByUserId(userId);
        Map<String, BigDecimal> tagAffinities = new LinkedHashMap<>();
        for (UserPreferenceTagAffinityPO affinity : affinities) {
            tagAffinities.put(affinity.getTagCode(), affinity.getAffinity());
        }
        return new UserPreferenceProfileDTO(
                profile.getDistanceSensitivity(),
                profile.getBudgetSensitivity(),
                profile.getTransferSensitivity(),
                profile.getHiddenGemAffinity(),
                profile.getProfileConfidence(),
                profile.getQuestionnaireVersion(),
                false,
                tagAffinities
        );
    }
}
