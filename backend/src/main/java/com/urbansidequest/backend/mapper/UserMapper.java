package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.po.UserPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

public interface UserMapper extends BaseMapper<UserPO> {

    @Insert("INSERT INTO users (phone, nickname) VALUES (#{phone}, #{nickname})")
    void insertByPhone(@Param("phone") String phone, @Param("nickname") String nickname);

    @Select("""
            SELECT id, phone, nickname, avatar_url, status, created_at, updated_at
            FROM users
            WHERE phone = #{phone}
            """)
    @Results(id = "userResultMap", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "phone", property = "phone"),
            @Result(column = "nickname", property = "nickname"),
            @Result(column = "avatar_url", property = "avatarUrl"),
            @Result(column = "status", property = "status"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    UserPO selectByPhone(@Param("phone") String phone);

    @Select("""
            SELECT id, phone, nickname, avatar_url, status, created_at, updated_at
            FROM users
            WHERE id = #{id,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            """)
    @Results(id = "userResultMapById", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "phone", property = "phone"),
            @Result(column = "nickname", property = "nickname"),
            @Result(column = "avatar_url", property = "avatarUrl"),
            @Result(column = "status", property = "status"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    UserPO selectByUserId(@Param("id") UUID id);
}
