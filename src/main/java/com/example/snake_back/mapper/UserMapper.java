package com.example.snake_back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.snake_back.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper for User
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 你可以在这里补充自定义方法（如果需要）
}