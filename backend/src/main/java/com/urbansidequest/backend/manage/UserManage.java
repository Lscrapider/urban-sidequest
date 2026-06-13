package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.UserPO;
import com.urbansidequest.backend.mapper.UserMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserManage extends ServiceImpl<UserMapper, UserPO> {

    private static final String DEFAULT_NICKNAME = "城市探索者";

    public Optional<UserPO> findByPhone(String phone) {
        return Optional.ofNullable(this.baseMapper.selectByPhone(phone));
    }

    public Optional<UserPO> findById(UUID id) {
        return Optional.ofNullable(this.baseMapper.selectByUserId(id));
    }

    public UserPO createByPhone(String phone) {
        this.baseMapper.insertByPhone(phone, DEFAULT_NICKNAME);
        return this.findByPhone(phone).orElseThrow();
    }
}
