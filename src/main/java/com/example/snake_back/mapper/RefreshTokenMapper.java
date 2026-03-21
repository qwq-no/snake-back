package com.example.snake_back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.snake_back.pojo.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis-Plus Mapper for User
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
    RefreshToken selectByTokenHash(@Param("tokenHash") String tokenHash);
    RefreshToken selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
    int revokeAllByUserId(@Param("userId") String userId, @Param("revokedAt") String revokedAt);
}